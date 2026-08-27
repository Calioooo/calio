package com.calio.calendar.integration.sync;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleCalendarSyncTokenExpiredException;
import com.calio.calendar.external.google.dto.GoogleCalendarEventPage;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.connection.service.GoogleCalendarAccessTokenService;
import com.calio.calendar.integration.connection.service.GoogleCalendarConnectionQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationLeaseService;
import com.calio.calendar.integration.sync.page.GoogleCalendarPageChangeService;
import com.calio.calendar.integration.sync.page.GoogleCalendarPageOwnership;
import com.calio.calendar.integration.sync.page.GoogleCalendarPageNormalizer;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarSyncService {

    private final GoogleCalendarConnectionQueryService connectionQueryService;
    private final GoogleCalendarIntegrationDataService integrationDataService;
    private final GoogleCalendarAccessTokenService accessTokenService;
    private final GoogleCalendarEventRequestService eventRequestService;
    private final GoogleCalendarPageChangeService pageChangeService;
    private final GoogleCalendarPageNormalizer pageNormalizer;
    private final GoogleOperationLeaseService operationLeaseService;

    public GoogleCalendarSyncService(
            GoogleCalendarConnectionQueryService connectionQueryService,
            GoogleCalendarIntegrationDataService integrationDataService,
            GoogleCalendarAccessTokenService accessTokenService,
            GoogleCalendarEventRequestService eventRequestService,
            GoogleCalendarPageChangeService pageChangeService,
            GoogleCalendarPageNormalizer pageNormalizer,
            GoogleOperationLeaseService operationLeaseService
    ) {
        this.connectionQueryService = connectionQueryService;
        this.integrationDataService = integrationDataService;
        this.accessTokenService = accessTokenService;
        this.eventRequestService = eventRequestService;
        this.pageChangeService = pageChangeService;
        this.pageNormalizer = pageNormalizer;
        this.operationLeaseService = operationLeaseService;
    }

    public void synchronize(
            Long jobId,
            Long accountId,
            String workerToken
    ) {
        operationLeaseService.extend(jobId, accountId, workerToken);
        GoogleCalendarConnection connection = connectionQueryService.getConnectedConnection(accountId);
        SyncExecution execution = new SyncExecution(
                connection.getId(),
                connection.getAccountId(),
                connection.getNextSyncToken(),
                workerToken
        );
        GoogleCalendarSyncRunContext context = new GoogleCalendarSyncRunContext(
                accessTokenService.getAccessToken(execution.connectionId())
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
                    execution.connectionId(),
                    mode,
                    execution.nextSyncToken(),
                    pageToken,
                    context
            );
            GoogleCalendarNormalizedPage normalizedPage = pageNormalizer.normalize(
                    execution.connectionId(), page, context);
            operationLeaseService.extend(jobId, execution.accountId(), execution.workerToken());
            pageChangeService.applyNormalizedPage(
                    execution.connectionId(),
                    execution.accountId(),
                    new GoogleCalendarPageOwnership(jobId, execution.workerToken()),
                    normalizedPage
            );
            nextSyncToken = page.nextSyncToken();
            pageToken = nextPageToken(page, seenPageTokens);
        } while (pageToken != null);
        operationLeaseService.extend(jobId, execution.accountId(), execution.workerToken());
        integrationDataService.completeSyncRun(
                jobId,
                execution.accountId(),
                execution.connectionId(),
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
            Long connectionId,
            Long accountId,
            String nextSyncToken,
            String workerToken
    ) {
    }
}
