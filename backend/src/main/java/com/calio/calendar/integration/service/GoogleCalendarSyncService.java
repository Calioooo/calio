package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleCalendarEventsClient;
import com.calio.calendar.external.google.GoogleCalendarSyncTokenExpiredException;
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
        GoogleCalendarSyncMode initialMode = modeFor(lease.nextSyncToken());
        boolean fullResetPerformed = false;
        try {
            accessTokenService.getAccessToken(lease.integrationId());
            if (initialMode == GoogleCalendarSyncMode.FULL) {
                resetProviderData(lease);
                fullResetPerformed = true;
            }
            GoogleCalendarSyncMode completedMode = synchronizeSelectedMode(lease, initialMode);
            return GoogleCalendarSyncResponse.from(completedMode);
        } catch (FullRecoveryFailure exception) {
            cleanupFailure(lease, true);
            throw exception.cause();
        } catch (RuntimeException exception) {
            cleanupFailure(lease, fullResetPerformed);
            throw exception;
        }
    }

    private GoogleCalendarSyncMode synchronizeSelectedMode(
            SyncLease lease,
            GoogleCalendarSyncMode mode
    ) {
        try {
            synchronizePages(lease, mode);
            return mode;
        } catch (GoogleCalendarSyncTokenExpiredException exception) {
            if (mode != GoogleCalendarSyncMode.INCREMENTAL) {
                throw new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED, exception);
            }
            return recoverExpiredCursorWithFullSync(lease);
        }
    }

    private GoogleCalendarSyncMode recoverExpiredCursorWithFullSync(SyncLease lease) {
        resetProviderData(lease);
        try {
            synchronizePages(lease, GoogleCalendarSyncMode.FULL);
            return GoogleCalendarSyncMode.FULL;
        } catch (RuntimeException exception) {
            throw new FullRecoveryFailure(exception);
        }
    }

    private void synchronizePages(SyncLease lease, GoogleCalendarSyncMode mode) {
        String pageToken = null;
        Set<String> seenPageTokens = new HashSet<>();
        do {
            GoogleCalendarEventPage page = eventsClient.listEvents(
                    lease.integrationId(),
                    mode,
                    mode == GoogleCalendarSyncMode.INCREMENTAL ? lease.nextSyncToken() : null,
                    pageToken
            );
            persistPage(lease, page);
            pageToken = nextPageToken(page, seenPageTokens);
        } while (pageToken != null);
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

    private void cleanupFailure(SyncLease lease, boolean cleanProviderData) {
        if (cleanProviderData) {
            providerDataService.cleanupFullFailureAndRelease(
                    lease.integrationId(),
                    lease.runId()
            );
            return;
        }
        providerDataService.releaseOwnedLease(lease.integrationId(), lease.runId());
    }

    private GoogleCalendarSyncMode modeFor(String nextSyncToken) {
        return nextSyncToken == null
                ? GoogleCalendarSyncMode.FULL
                : GoogleCalendarSyncMode.INCREMENTAL;
    }

    private static final class FullRecoveryFailure extends RuntimeException {

        private final RuntimeException cause;

        private FullRecoveryFailure(RuntimeException cause) {
            super(cause);
            this.cause = cause;
        }

        private RuntimeException cause() {
            return cause;
        }
    }
}
