package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleCalendarEventsClient;
import com.calio.calendar.external.google.GoogleCalendarSyncTokenExpiredException;
import com.calio.calendar.external.google.GoogleCalendarUnauthorizedException;
import com.calio.calendar.external.google.dto.GoogleCalendarEventPage;
import com.calio.calendar.integration.controller.dto.GoogleCalendarSyncResponse;
import com.calio.calendar.integration.domain.GoogleCalendarSyncMode;
import com.calio.calendar.integration.service.GoogleCalendarSyncLeaseService.SyncLease;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarSyncService {

    private final GoogleCalendarSyncLeaseService leaseService;
    private final GoogleCalendarProviderDataService providerDataService;
    private final GoogleCalendarAccessTokenService accessTokenService;
    private final GoogleCalendarEventsClient eventsClient;
    private final GoogleCalendarEventPagePersistenceService pagePersistenceService;
    private final GoogleCalendarPageNormalizer pageNormalizer;

    public GoogleCalendarSyncService(
            GoogleCalendarSyncLeaseService leaseService,
            GoogleCalendarProviderDataService providerDataService,
            GoogleCalendarAccessTokenService accessTokenService,
            GoogleCalendarEventsClient eventsClient,
            GoogleCalendarEventPagePersistenceService pagePersistenceService,
            GoogleCalendarPageNormalizer pageNormalizer
    ) {
        this.leaseService = leaseService;
        this.providerDataService = providerDataService;
        this.accessTokenService = accessTokenService;
        this.eventsClient = eventsClient;
        this.pagePersistenceService = pagePersistenceService;
        this.pageNormalizer = pageNormalizer;
    }

    public GoogleCalendarSyncResponse sync(Long accountId) {
        SyncLease lease = leaseService.acquire(accountId, UUID.randomUUID().toString());
        GoogleCalendarSyncRunContext context;
        try {
            context = new GoogleCalendarSyncRunContext(
                    accessTokenService.getAccessToken(lease.integrationId())
            );
        } catch (RuntimeException exception) {
            throw releaseOwnedLeasePreservingFailure(lease, exception);
        }

        try {
            SyncExecution result = synchronize(lease, context, false);
            leaseService.release(lease);
            return GoogleCalendarSyncResponse.from(result.mode());
        } catch (RuntimeException exception) {
            throw releaseOwnedLeasePreservingFailure(lease, exception);
        }
    }

    public SyncOperationResult performOwnedSync(SyncLease lease) {
        GoogleCalendarSyncRunContext context = new GoogleCalendarSyncRunContext(
                accessTokenService.getAccessToken(lease.integrationId())
        );
        SyncExecution result = synchronize(lease, context, true);
        return new SyncOperationResult(result.nextSyncToken(), result.conflictDetected());
    }

    private SyncExecution synchronize(
            SyncLease lease,
            GoogleCalendarSyncRunContext context,
            boolean deferCursorCommit
    ) {
        if (modeFor(lease.nextSyncToken()) == GoogleCalendarSyncMode.FULL) {
            return synchronizePages(
                    lease, GoogleCalendarSyncMode.FULL, context, deferCursorCommit
            );
        }
        try {
            return synchronizePages(
                    lease, GoogleCalendarSyncMode.INCREMENTAL, context, deferCursorCommit
            );
        } catch (GoogleCalendarSyncTokenExpiredException exception) {
            context.resetSeenIdentities();
            return synchronizePages(
                    lease, GoogleCalendarSyncMode.FULL, context, deferCursorCommit
            );
        }
    }

    private SyncExecution synchronizePages(
            SyncLease lease,
            GoogleCalendarSyncMode mode,
            GoogleCalendarSyncRunContext context,
            boolean deferCursorCommit
    ) {
        String pageToken = null;
        String nextSyncToken = null;
        Set<String> seenPageTokens = new HashSet<>();
        boolean conflictDetected = false;
        do {
            GoogleCalendarEventPage page = requestPage(lease, mode, pageToken, context);
            GoogleCalendarNormalizedPage normalizedPage = pageNormalizer.normalize(
                    lease.integrationId(),
                    page,
                    context
            );
            conflictDetected |= pagePersistenceService.persistNormalizedPage(
                    lease.integrationId(),
                    lease.accountId(),
                    lease.runId(),
                    normalizedPage
            );
            nextSyncToken = page.nextSyncToken();
            pageToken = nextPageToken(page, seenPageTokens);
        } while (pageToken != null);
        conflictDetected |= finishReconciliation(
                lease, mode, context, nextSyncToken, deferCursorCommit
        );
        return new SyncExecution(mode, nextSyncToken, conflictDetected);
    }

    private boolean finishReconciliation(
            SyncLease lease,
            GoogleCalendarSyncMode mode,
            GoogleCalendarSyncRunContext context,
            String nextSyncToken,
            boolean deferCursorCommit
    ) {
        if (deferCursorCommit) {
            return providerDataService.prepareReconciliation(
                    lease.integrationId(), lease.runId(), mode,
                    context.seenEventIds(), context.seenRecurrenceEventIds(),
                    context.seenRecurrenceEventOverrideIds(), nextSyncToken
            );
        }
        return providerDataService.finalizeReconciliation(
                lease.integrationId(), lease.runId(), mode,
                context.seenEventIds(), context.seenRecurrenceEventIds(),
                context.seenRecurrenceEventOverrideIds(), nextSyncToken
        );
    }

    private GoogleCalendarEventPage requestPage(
            SyncLease lease,
            GoogleCalendarSyncMode mode,
            String pageToken,
            GoogleCalendarSyncRunContext context
    ) {
        try {
            return listEvents(lease, mode, pageToken, context.accessToken());
        } catch (GoogleCalendarUnauthorizedException exception) {
            String refreshedAccessToken = accessTokenService.forceRefresh(lease.integrationId());
            context.replaceAccessToken(refreshedAccessToken);
            try {
                return listEvents(lease, mode, pageToken, refreshedAccessToken);
            } catch (GoogleCalendarUnauthorizedException retryException) {
                throw new CalioException(
                        ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED,
                        retryException
                );
            }
        }
    }

    private GoogleCalendarEventPage listEvents(
            SyncLease lease,
            GoogleCalendarSyncMode mode,
            String pageToken,
            String accessToken
    ) {
        return eventsClient.listEvents(
                accessToken,
                mode,
                mode == GoogleCalendarSyncMode.INCREMENTAL ? lease.nextSyncToken() : null,
                pageToken
        );
    }

    private String nextPageToken(GoogleCalendarEventPage page, Set<String> seenPageTokens) {
        String nextPageToken = page.nextPageToken();
        if (nextPageToken != null && !seenPageTokens.add(nextPageToken)) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
        }
        return nextPageToken;
    }

    private RuntimeException releaseOwnedLeasePreservingFailure(
            SyncLease lease,
            RuntimeException failure
    ) {
        try {
            providerDataService.releaseOwnedLease(lease.integrationId(), lease.runId());
        } catch (RuntimeException releaseException) {
            failure.addSuppressed(releaseException);
        }
        return failure;
    }

    private GoogleCalendarSyncMode modeFor(String nextSyncToken) {
        return nextSyncToken == null
                ? GoogleCalendarSyncMode.FULL
                : GoogleCalendarSyncMode.INCREMENTAL;
    }

    public record SyncOperationResult(String nextSyncToken, boolean conflictDetected) {
    }

    private record SyncExecution(
            GoogleCalendarSyncMode mode,
            String nextSyncToken,
            boolean conflictDetected
    ) {
    }
}
