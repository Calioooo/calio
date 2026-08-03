package com.calio.calendar.integration.service;

import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.repository.GoogleCalendarOperationJobRepository;
import java.time.Instant;
import java.time.Clock;
import com.calio.calendar.integration.domain.GoogleCalendarOperationJob;
import com.calio.calendar.integration.domain.GoogleCalendarOperationTrigger;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class GoogleCalendarIntegrationPersistenceService {

    private final GoogleCalendarIntegrationRepository googleCalendarIntegrationRepository;
    private final GoogleCalendarOperationJobRepository operationJobRepository;
    private final GoogleCalendarOperationWakeup wakeup;
    private final Clock clock;

    @Autowired
    public GoogleCalendarIntegrationPersistenceService(
            GoogleCalendarIntegrationRepository googleCalendarIntegrationRepository,
            GoogleCalendarOperationJobRepository operationJobRepository,
            GoogleCalendarOperationWakeup wakeup,
            Clock clock
    ) {
        this.googleCalendarIntegrationRepository = googleCalendarIntegrationRepository;
        this.operationJobRepository = operationJobRepository;
        this.wakeup = wakeup;
        this.clock = clock;
    }

    protected GoogleCalendarIntegrationPersistenceService(
            GoogleCalendarIntegrationRepository googleCalendarIntegrationRepository,
            GoogleCalendarProviderDataService providerDataService
    ) {
        this(googleCalendarIntegrationRepository, null, null, null);
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
        GoogleCalendarIntegration integration = googleCalendarIntegrationRepository
                .findByAccountIdForUpdate(accountId)
                .map(existingIntegration -> replace(
                        existingIntegration,
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
        enqueueFullInventory(integration);
        return integration;
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
        operationJobRepository.deleteAllByIntegrationId(integration.getId());
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
                    operationJobRepository.deleteAllByIntegrationId(integration.getId());
                    integration.disconnect(Instant.now(clock));
                });
    }

    private void enqueueFullInventory(GoogleCalendarIntegration integration) {
        long sequence = integration.allocateOperationSequence();
        operationJobRepository.save(GoogleCalendarOperationJob.sync(
                integration,
                sequence,
                GoogleCalendarOperationTrigger.PERIODIC,
                Instant.now(clock)
        ));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                wakeup.wakeUp();
            }
        });
    }
}
