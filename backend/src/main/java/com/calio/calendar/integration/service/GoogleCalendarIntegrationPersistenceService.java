package com.calio.calendar.integration.service;

import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleCalendarIntegrationPersistenceService {

    private final GoogleCalendarIntegrationQueryService integrationQueryService;
    private final GoogleCalendarIntegrationCommandService integrationCommandService;
    private final GoogleCalendarProviderDataService providerDataService;
    private final GoogleOperationJobCommandService jobCommandService;

    @Autowired
    public GoogleCalendarIntegrationPersistenceService(
            GoogleCalendarIntegrationQueryService integrationQueryService,
            GoogleCalendarIntegrationCommandService integrationCommandService,
            GoogleCalendarProviderDataService providerDataService,
            GoogleOperationJobCommandService jobCommandService
    ) {
        this.integrationQueryService = integrationQueryService;
        this.integrationCommandService = integrationCommandService;
        this.providerDataService = providerDataService;
        this.jobCommandService = jobCommandService;
    }

    @Transactional
    public GoogleCalendarIntegration saveOrReplace(
            Long accountId,
            String googleSubject,
            String googleEmail,
            String encryptedRefreshToken,
            String encryptedAccessToken,
            Instant accessTokenExpiresAt,
            Instant connectedAt
    ) {
        return integrationCommandService.tryLockIntegration(accountId)
                .map(integration -> replace(
                        integration,
                        googleSubject,
                        googleEmail,
                        encryptedRefreshToken,
                        encryptedAccessToken,
                        accessTokenExpiresAt,
                        connectedAt
                ))
                .orElseGet(() -> integrationCommandService.createIntegration(
                        accountId,
                        googleSubject,
                        googleEmail,
                        encryptedRefreshToken,
                        encryptedAccessToken,
                        accessTokenExpiresAt,
                        connectedAt
                ));
    }

    private GoogleCalendarIntegration replace(
            GoogleCalendarIntegration integration,
            String googleSubject,
            String googleEmail,
            String encryptedRefreshToken,
            String encryptedAccessToken,
            Instant accessTokenExpiresAt,
            Instant connectedAt
    ) {
        providerDataService.deleteProviderData(integration.getId());
        return integrationCommandService.replaceIntegration(
                integration,
                googleSubject,
                googleEmail,
                encryptedRefreshToken,
                encryptedAccessToken,
                accessTokenExpiresAt,
                connectedAt
        );
    }

    @Transactional(readOnly = true)
    public GoogleCalendarIntegration findByAccountIdOrNull(Long accountId) {
        return integrationQueryService.getIntegrationIfExists(accountId).orElse(null);
    }

    @Transactional
    public void deleteByAccountId(Long accountId) {
        integrationCommandService.tryLockIntegration(accountId)
                .ifPresent(integration -> {
                    jobCommandService.deleteJobsForIntegration(integration.getId());
                    providerDataService.deleteProviderData(integration.getId());
                    integrationCommandService.deleteIntegration(integration);
                });
    }
}
