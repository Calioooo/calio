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

    public GoogleCalendarSyncService(
            GoogleCalendarSyncLeaseService leaseService,
            GoogleCalendarProviderDataService providerDataService,
            GoogleCalendarAccessTokenService accessTokenService,
            GoogleCalendarEventsClient eventsClient,
            GoogleCalendarEventPagePersistenceService pagePersistenceService
    ) {
        this.leaseService = leaseService;
        this.providerDataService = providerDataService;
        this.accessTokenService = accessTokenService;
        this.eventsClient = eventsClient;
        this.pagePersistenceService = pagePersistenceService;
    }

    public GoogleCalendarSyncResponse sync(Long accountId) {
        SyncLease lease = leaseService.acquire(accountId, UUID.randomUUID().toString());
        String accessToken;
        try {
            accessToken = accessTokenService.getAccessToken(lease.integrationId());
        } catch (RuntimeException exception) {
            providerDataService.releaseOwnedLease(lease.integrationId(), lease.runId());
            throw exception;
        }

        GoogleCalendarSyncMode completedMode = modeFor(lease.nextSyncToken())
                == GoogleCalendarSyncMode.FULL
                ? synchronizeFull(lease, accessToken)
                : synchronizeIncremental(lease, accessToken);
        return GoogleCalendarSyncResponse.from(completedMode);
    }

    private GoogleCalendarSyncMode synchronizeFull(
            SyncLease lease,
            String accessToken
    ) {
        try {
            resetProviderData(lease);
        } catch (RuntimeException exception) {
            providerDataService.releaseOwnedLease(lease.integrationId(), lease.runId());
            throw exception;
        }

        try {
            synchronizePages(lease, GoogleCalendarSyncMode.FULL, accessToken);
            return GoogleCalendarSyncMode.FULL;
        } catch (GoogleCalendarSyncTokenExpiredException exception) {
            cleanupFullFailure(lease);
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED, exception);
        } catch (RuntimeException exception) {
            cleanupFullFailure(lease);
            throw exception;
        }
    }

    private GoogleCalendarSyncMode synchronizeIncremental(
            SyncLease lease,
            String accessToken
    ) {
        try {
            synchronizePages(lease, GoogleCalendarSyncMode.INCREMENTAL, accessToken);
            return GoogleCalendarSyncMode.INCREMENTAL;
        } catch (GoogleCalendarSyncTokenExpiredException exception) {
            return synchronizeFull(lease, accessToken);
        } catch (RuntimeException exception) {
            providerDataService.releaseOwnedLease(lease.integrationId(), lease.runId());
            throw exception;
        }
    }

    private void synchronizePages(
            SyncLease lease,
            GoogleCalendarSyncMode mode,
            String initialAccessToken
    ) {
        String pageToken = null;
        String accessToken = initialAccessToken;
        Set<String> seenPageTokens = new HashSet<>();
        do {
            PageRequestResult result = requestPage(
                    lease,
                    mode,
                    pageToken,
                    accessToken
            );
            GoogleCalendarEventPage page = result.page();
            accessToken = result.accessToken();
            persistPage(lease, page);
            pageToken = nextPageToken(page, seenPageTokens);
        } while (pageToken != null);
    }

    private PageRequestResult requestPage(
            SyncLease lease,
            GoogleCalendarSyncMode mode,
            String pageToken,
            String accessToken
    ) {
        try {
            return new PageRequestResult(
                    listEvents(lease, mode, pageToken, accessToken),
                    accessToken
            );
        } catch (GoogleCalendarUnauthorizedException exception) {
            String refreshedAccessToken = accessTokenService.forceRefresh(lease.integrationId());
            try {
                return new PageRequestResult(
                        listEvents(lease, mode, pageToken, refreshedAccessToken),
                        refreshedAccessToken
                );
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

    private void persistPage(SyncLease lease, GoogleCalendarEventPage page) {
        if (page.hasNextPage()) {
            pagePersistenceService.persistPage(
                    lease.integrationId(),
                    lease.accountId(),
                    lease.runId(),
                    page
            );
            return;
        }
        pagePersistenceService.persistLastPageAndFinalize(
                lease.integrationId(),
                lease.accountId(),
                lease.runId(),
                page
        );
    }

    private String nextPageToken(
            GoogleCalendarEventPage page,
            Set<String> seenPageTokens
    ) {
        String nextPageToken = page.nextPageToken();
        if (nextPageToken != null && !seenPageTokens.add(nextPageToken)) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
        }
        return nextPageToken;
    }

    private void resetProviderData(SyncLease lease) {
        boolean reset = providerDataService.resetUnderLease(
                lease.integrationId(),
                lease.runId()
        );
        if (!reset) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_CONFLICT);
        }
    }

    private void cleanupFullFailure(SyncLease lease) {
        providerDataService.cleanupFullFailureAndRelease(
                lease.integrationId(),
                lease.runId()
        );
    }

    private GoogleCalendarSyncMode modeFor(String nextSyncToken) {
        return nextSyncToken == null
                ? GoogleCalendarSyncMode.FULL
                : GoogleCalendarSyncMode.INCREMENTAL;
    }

    private record PageRequestResult(
            GoogleCalendarEventPage page,
            String accessToken
    ) {
    }
}
