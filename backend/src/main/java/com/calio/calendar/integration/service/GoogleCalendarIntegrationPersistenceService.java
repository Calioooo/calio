package com.calio.calendar.integration.service;

import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.repository.GoogleOperationJobRepository;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleCalendarIntegrationPersistenceService {

    private final GoogleCalendarIntegrationRepository googleCalendarIntegrationRepository;
    private final GoogleCalendarProviderDataService providerDataService;
    private final GoogleOperationJobRepository jobRepository;

    @Autowired
    public GoogleCalendarIntegrationPersistenceService(
            GoogleCalendarIntegrationRepository googleCalendarIntegrationRepository,
            GoogleCalendarProviderDataService providerDataService,
            GoogleOperationJobRepository jobRepository
    ) {
        this.googleCalendarIntegrationRepository = googleCalendarIntegrationRepository;
        this.providerDataService = providerDataService;
        this.jobRepository = jobRepository;
    }

    protected GoogleCalendarIntegrationPersistenceService(
            GoogleCalendarIntegrationRepository googleCalendarIntegrationRepository,
            GoogleCalendarProviderDataService providerDataService
    ) {
        this(googleCalendarIntegrationRepository, providerDataService, null);
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
        return googleCalendarIntegrationRepository.findByAccountIdForUpdate(accountId)
                .map(integration -> replace(
                        integration,
                        googleSubject,
                        googleEmail,
                        encryptedRefreshToken,
                        encryptedAccessToken,
                        accessTokenExpiresAt,
                        connectedAt
                ))
                .orElseGet(() -> googleCalendarIntegrationRepository.saveAndFlush(new GoogleCalendarIntegration(
                        accountId,
                        googleSubject,
                        googleEmail,
                        encryptedRefreshToken,
                        encryptedAccessToken,
                        accessTokenExpiresAt,
                        connectedAt
                )));
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
        integration.replace(
                googleSubject,
                googleEmail,
                encryptedRefreshToken,
                encryptedAccessToken,
                accessTokenExpiresAt,
                connectedAt
        );
        return googleCalendarIntegrationRepository.saveAndFlush(integration);
    }

    @Transactional(readOnly = true)
    public GoogleCalendarIntegration findByAccountIdOrNull(Long accountId) {
        return googleCalendarIntegrationRepository.findByAccountId(accountId).orElse(null);
    }

    @Transactional
    public void deleteByAccountId(Long accountId) {
        googleCalendarIntegrationRepository.findByAccountIdForUpdate(accountId)
                .ifPresent(integration -> {
                    if (jobRepository != null) {
                        jobRepository.deleteByIntegrationId(integration.getId());
                    }
                    providerDataService.deleteProviderData(integration.getId());
                    googleCalendarIntegrationRepository.delete(integration);
                });
    }
}
