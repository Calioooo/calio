package com.calio.calendar.integration.service;

import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleCalendarSyncLeaseService {

    private final GoogleCalendarIntegrationQueryService integrationQueryService;
    private final GoogleCalendarIntegrationCommandService integrationCommandService;

    public GoogleCalendarSyncLeaseService(
            GoogleCalendarIntegrationQueryService integrationQueryService,
            GoogleCalendarIntegrationCommandService integrationCommandService
    ) {
        this.integrationQueryService = integrationQueryService;
        this.integrationCommandService = integrationCommandService;
    }

    @Transactional
    public SyncLease acquire(Long accountId, String runId) {
        GoogleCalendarIntegration integration = integrationQueryService.getIntegration(accountId);
        integrationCommandService.acquireSyncLease(accountId, runId);
        return new SyncLease(
                integration.getId(),
                integration.getAccountId(),
                integration.getNextSyncToken(),
                runId
        );
    }

    public record SyncLease(
            Long integrationId,
            Long accountId,
            String nextSyncToken,
            String runId
    ) {
    }
}
