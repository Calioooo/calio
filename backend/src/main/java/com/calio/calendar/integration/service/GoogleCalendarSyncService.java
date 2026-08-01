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
            GoogleCalendarSyncMode completedMode = synchronize(lease, context);
            return GoogleCalendarSyncResponse.from(completedMode);
        } catch (RuntimeException exception) {
            throw releaseOwnedLeasePreservingFailure(lease, exception);
        }
    }

    private GoogleCalendarSyncMode synchronize(
            SyncLease lease,
            GoogleCalendarSyncRunContext context
    ) {
        if (modeFor(lease.nextSyncToken()) == GoogleCalendarSyncMode.FULL) {
            synchronizePages(lease, GoogleCalendarSyncMode.FULL, context);
            return GoogleCalendarSyncMode.FULL;
        }
        try {
            synchronizePages(lease, GoogleCalendarSyncMode.INCREMENTAL, context);
            return GoogleCalendarSyncMode.INCREMENTAL;
        } catch (GoogleCalendarSyncTokenExpiredException exception) {
            context.resetSeenIdentities();
            synchronizePages(lease, GoogleCalendarSyncMode.FULL, context);
            return GoogleCalendarSyncMode.FULL;
        }
    }

    private void synchronizePages(
            SyncLease lease,
            GoogleCalendarSyncMode mode,
            GoogleCalendarSyncRunContext context
    ) {
        String pageToken = null;
        String nextSyncToken = null;
        Set<String> seenPageTokens = new HashSet<>();
        do {
            GoogleCalendarEventPage page = requestPage(lease, mode, pageToken, context);
            GoogleCalendarNormalizedPage normalizedPage = pageNormalizer.normalize(
                    lease.integrationId(),
                    page,
                    context
            );
            pagePersistenceService.persistNormalizedPage(
                    lease.integrationId(),
                    lease.accountId(),
                    lease.runId(),
                    normalizedPage
            );
            nextSyncToken = page.nextSyncToken();
            pageToken = nextPageToken(page, seenPageTokens);
        } while (pageToken != null);
        providerDataService.finalizeReconciliation(
                lease.integrationId(),
                lease.runId(),
                mode == GoogleCalendarSyncMode.FULL,
                context.seenGeneralEventIds(),
                context.seenRecurrenceMasterIds(),
                context.seenRecurrenceOverrideIds(),
                nextSyncToken
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
}
