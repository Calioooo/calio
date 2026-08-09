package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleCalendarSyncTokenExpiredException;
import com.calio.calendar.external.google.dto.GoogleCalendarEventPage;
import com.calio.calendar.integration.domain.GoogleCalendarSyncMode;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage;
import com.calio.calendar.integration.service.dto.GoogleCalendarSyncLease;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarSyncService {

    private final GoogleCalendarSyncLeaseService leaseService;
    private final GoogleCalendarProviderDataService providerDataService;
    private final GoogleCalendarAccessTokenService accessTokenService;
    private final GoogleCalendarEventRequestService eventRequestService;
    private final GoogleCalendarEventPagePersistenceService pagePersistenceService;
    private final GoogleCalendarPageNormalizer pageNormalizer;
    private final GoogleOperationJobPersistenceService operationJobPersistenceService;

    public GoogleCalendarSyncService(
            GoogleCalendarSyncLeaseService leaseService,
            GoogleCalendarProviderDataService providerDataService,
            GoogleCalendarAccessTokenService accessTokenService,
            GoogleCalendarEventRequestService eventRequestService,
            GoogleCalendarEventPagePersistenceService pagePersistenceService,
            GoogleCalendarPageNormalizer pageNormalizer,
            GoogleOperationJobPersistenceService operationJobPersistenceService
    ) {
        this.leaseService = leaseService;
        this.providerDataService = providerDataService;
        this.accessTokenService = accessTokenService;
        this.eventRequestService = eventRequestService;
        this.pagePersistenceService = pagePersistenceService;
        this.pageNormalizer = pageNormalizer;
        this.operationJobPersistenceService = operationJobPersistenceService;
    }

    public void synchronize(
            Long jobId,
            Long accountId,
            String workerToken
    ) {
        GoogleCalendarSyncLease lease = leaseService.acquire(accountId, workerToken);
        try {
            assertOwned(jobId, accountId, workerToken);
            GoogleCalendarSyncRunContext context = new GoogleCalendarSyncRunContext(
                    accessTokenService.getAccessToken(lease.integrationId())
            );
            synchronize(jobId, lease, context);
        } catch (RuntimeException exception) {
            throw releaseSyncLeaseAfterFailure(lease, exception);
        }
    }

    private void synchronize(
            Long jobId,
            GoogleCalendarSyncLease lease,
            GoogleCalendarSyncRunContext context
    ) {
        if (modeFor(lease.nextSyncToken()) == GoogleCalendarSyncMode.FULL) {
            synchronizePages(jobId, lease, GoogleCalendarSyncMode.FULL, context);
            return;
        }
        try {
            synchronizePages(jobId, lease, GoogleCalendarSyncMode.INCREMENTAL, context);
        } catch (GoogleCalendarSyncTokenExpiredException exception) {
            context.resetSeenIdentities();
            synchronizePages(jobId, lease, GoogleCalendarSyncMode.FULL, context);
        }
    }

    private void synchronizePages(
            Long jobId,
            GoogleCalendarSyncLease lease,
            GoogleCalendarSyncMode mode,
            GoogleCalendarSyncRunContext context
    ) {
        String pageToken = null;
        String nextSyncToken = null;
        Set<String> seenPageTokens = new HashSet<>();
        do {
            assertOwned(jobId, lease.accountId(), lease.runId());
            GoogleCalendarEventPage page = eventRequestService.listEvents(
                    lease.integrationId(),
                    mode,
                    lease.nextSyncToken(),
                    pageToken,
                    context
            );
            GoogleCalendarNormalizedPage normalizedPage = pageNormalizer.normalize(
                    lease.integrationId(), page, context);
            assertOwned(jobId, lease.accountId(), lease.runId());
            pagePersistenceService.persistSyncPage(
                    jobId, lease.integrationId(), lease.accountId(), lease.runId(), normalizedPage);
            nextSyncToken = page.nextSyncToken();
            pageToken = nextPageToken(page, seenPageTokens);
        } while (pageToken != null);
        assertOwned(jobId, lease.accountId(), lease.runId());
        providerDataService.completeSyncRun(
                jobId, lease.accountId(), lease.integrationId(), lease.runId(), mode,
                context.seenEventIds(), context.seenRecurrenceEventIds(),
                context.seenRecurrenceEventOverrideIds(), nextSyncToken);
    }

    private void assertOwned(Long jobId, Long accountId, String workerToken) {
        operationJobPersistenceService.extendOperationLease(jobId, accountId, workerToken);
    }

    private String nextPageToken(GoogleCalendarEventPage page, Set<String> seenPageTokens) {
        String nextPageToken = page.nextPageToken();
        if (nextPageToken != null && !seenPageTokens.add(nextPageToken)) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
        }
        return nextPageToken;
    }

    private RuntimeException releaseSyncLeaseAfterFailure(
            GoogleCalendarSyncLease lease,
            RuntimeException failure
    ) {
        try {
            providerDataService.releaseSyncLease(lease.integrationId(), lease.runId());
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
