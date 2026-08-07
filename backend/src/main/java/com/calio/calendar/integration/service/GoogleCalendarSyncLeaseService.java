package com.calio.calendar.integration.service;

import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.service.dto.GoogleCalendarSyncLease;
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
    public GoogleCalendarSyncLease acquire(Long accountId, String runId) {
        GoogleCalendarIntegration integration = integrationQueryService.getIntegration(accountId);
        integrationCommandService.acquireSyncLease(accountId, runId);
        return new GoogleCalendarSyncLease(
                integration.getId(),
                integration.getAccountId(),
                integration.getNextSyncToken(),
                runId
        );
    }

}
