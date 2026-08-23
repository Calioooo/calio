package com.calio.calendar.integration.connection.service;

import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobCommandService;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleCalendarIntegrationLifecycleService {

    private final GoogleCalendarIntegrationCommandService integrationCommandService;
    private final GoogleOperationJobCommandService jobCommandService;

    public GoogleCalendarIntegrationLifecycleService(
            GoogleCalendarIntegrationCommandService integrationCommandService,
            GoogleOperationJobCommandService jobCommandService
    ) {
        this.integrationCommandService = integrationCommandService;
        this.jobCommandService = jobCommandService;
    }

    @Transactional
    public Optional<String> disconnectConnectedIntegration(Long accountId, Instant disconnectedAt) {
        return integrationCommandService.tryLockIntegration(accountId)
                .filter(GoogleCalendarIntegration::isConnected)
                .map(integration -> disconnect(integration, disconnectedAt));
    }

    @Transactional
    public void disconnectAfterInvalidGrant(Long integrationId, Instant disconnectedAt) {
        integrationCommandService.tryLockIntegrationById(integrationId)
                .filter(GoogleCalendarIntegration::isConnected)
                .ifPresent(integration -> disconnect(integration, disconnectedAt));
    }

    @Transactional
    public void markSyncError(Long accountId, String reason, Instant occurredAt) {
        integrationCommandService.tryLockIntegration(accountId)
                .filter(GoogleCalendarIntegration::isConnected)
                .ifPresent(integration -> integrationCommandService.markIntegrationSyncError(
                        integration, reason, occurredAt));
    }

    private String disconnect(GoogleCalendarIntegration integration, Instant disconnectedAt) {
        String encryptedRefreshToken = integration.getEncryptedRefreshToken();
        jobCommandService.deleteJobsForIntegration(integration.getId());
        integrationCommandService.disconnectIntegration(integration, disconnectedAt);
        return encryptedRefreshToken;
    }
}
