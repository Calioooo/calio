package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleCalendarSyncTokenExpiredException;
import com.calio.calendar.external.google.dto.GoogleCalendarEventPage;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.domain.GoogleCalendarSyncMode;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarSyncService {

    private final GoogleCalendarIntegrationQueryService integrationQueryService;
    private final GoogleCalendarProviderDataService providerDataService;
    private final GoogleCalendarAccessTokenService accessTokenService;
    private final GoogleCalendarEventRequestService eventRequestService;
    private final GoogleCalendarEventPagePersistenceService pagePersistenceService;
    private final GoogleCalendarPageNormalizer pageNormalizer;
    private final GoogleOperationLeaseService operationLeaseService;

    public GoogleCalendarSyncService(
            GoogleCalendarIntegrationQueryService integrationQueryService,
            GoogleCalendarProviderDataService providerDataService,
            GoogleCalendarAccessTokenService accessTokenService,
            GoogleCalendarEventRequestService eventRequestService,
            GoogleCalendarEventPagePersistenceService pagePersistenceService,
            GoogleCalendarPageNormalizer pageNormalizer,
            GoogleOperationLeaseService operationLeaseService
    ) {
        this.integrationQueryService = integrationQueryService;
        this.providerDataService = providerDataService;
        this.accessTokenService = accessTokenService;
        this.eventRequestService = eventRequestService;
        this.pagePersistenceService = pagePersistenceService;
        this.pageNormalizer = pageNormalizer;
        this.operationLeaseService = operationLeaseService;
    }

    public void synchronize(
            Long jobId,
            Long accountId,
            String workerToken
    ) {
        operationLeaseService.extend(jobId, accountId, workerToken);
        GoogleCalendarIntegration integration = integrationQueryService.getIntegration(accountId);
        SyncExecution execution = new SyncExecution(
                integration.getId(),
                integration.getAccountId(),
                integration.getNextSyncToken(),
                workerToken
        );
        GoogleCalendarSyncRunContext context = new GoogleCalendarSyncRunContext(
                accessTokenService.getAccessToken(execution.integrationId())
        );
        synchronize(jobId, execution, context);
    }

    private void synchronize(
            Long jobId,
            SyncExecution execution,
            GoogleCalendarSyncRunContext context
    ) {
        if (modeFor(execution.nextSyncToken()) == GoogleCalendarSyncMode.FULL) {
            synchronizePages(jobId, execution, GoogleCalendarSyncMode.FULL, context);
            return;
        }
        try {
            synchronizePages(jobId, execution, GoogleCalendarSyncMode.INCREMENTAL, context);
        } catch (GoogleCalendarSyncTokenExpiredException exception) {
            context.resetSeenIdentities();
            synchronizePages(jobId, execution, GoogleCalendarSyncMode.FULL, context);
        }
    }

    private void synchronizePages(
            Long jobId,
            SyncExecution execution,
            GoogleCalendarSyncMode mode,
            GoogleCalendarSyncRunContext context
    ) {
        String pageToken = null;
        String nextSyncToken = null;
        Set<String> seenPageTokens = new HashSet<>();
        do {
            operationLeaseService.extend(jobId, execution.accountId(), execution.workerToken());
            GoogleCalendarEventPage page = eventRequestService.listEvents(
                    execution.integrationId(),
                    mode,
                    execution.nextSyncToken(),
                    pageToken,
                    context
            );
            GoogleCalendarNormalizedPage normalizedPage = pageNormalizer.normalize(
                    execution.integrationId(), page, context);
            operationLeaseService.extend(jobId, execution.accountId(), execution.workerToken());
            pagePersistenceService.persistNormalizedPage(
                    execution.integrationId(),
                    execution.accountId(),
                    normalizedPage
            );
            nextSyncToken = page.nextSyncToken();
            pageToken = nextPageToken(page, seenPageTokens);
        } while (pageToken != null);
        operationLeaseService.extend(jobId, execution.accountId(), execution.workerToken());
        providerDataService.completeSyncRun(
                jobId,
                execution.accountId(),
                execution.integrationId(),
                execution.workerToken(),
                mode,
                context.seenEventIds(), context.seenRecurrenceEventIds(),
                context.seenRecurrenceEventOverrideIds(), nextSyncToken);
    }

    private String nextPageToken(GoogleCalendarEventPage page, Set<String> seenPageTokens) {
        String nextPageToken = page.nextPageToken();
        if (nextPageToken != null && !seenPageTokens.add(nextPageToken)) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
        }
        return nextPageToken;
    }

    private GoogleCalendarSyncMode modeFor(String nextSyncToken) {
        return nextSyncToken == null
                ? GoogleCalendarSyncMode.FULL
                : GoogleCalendarSyncMode.INCREMENTAL;
    }

    private record SyncExecution(
            Long integrationId,
            Long accountId,
            String nextSyncToken,
            String workerToken
    ) {
    }
}
