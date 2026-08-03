package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.domain.GoogleCalendarIntegrationState;
import com.calio.calendar.integration.domain.GoogleCalendarOperationJob;
import com.calio.calendar.integration.domain.GoogleCalendarOperationTrigger;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.repository.GoogleCalendarOperationJobRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class GoogleCalendarOperationEnqueueService {

    private final GoogleCalendarIntegrationRepository integrationRepository;
    private final GoogleCalendarOperationJobRepository jobRepository;
    private final GoogleCalendarOperationWakeup wakeup;
    private final Clock clock;

    public GoogleCalendarOperationEnqueueService(
            GoogleCalendarIntegrationRepository integrationRepository,
            GoogleCalendarOperationJobRepository jobRepository,
            GoogleCalendarOperationWakeup wakeup,
            Clock clock
    ) {
        this.integrationRepository = integrationRepository;
        this.jobRepository = jobRepository;
        this.wakeup = wakeup;
        this.clock = clock;
    }

    @Transactional
    public void enqueueManualSync(Long accountId) {
        GoogleCalendarIntegration integration = connectedIntegrationForUpdate(accountId);
        saveSyncJob(integration, GoogleCalendarOperationTrigger.MANUAL);
    }

    @Transactional
    public boolean enqueuePeriodicSync(Long accountId) {
        GoogleCalendarIntegration integration = integrationRepository
                .findByAccountIdForUpdate(accountId)
                .orElse(null);
        if (integration == null || !integration.isConnected()) {
            return false;
        }
        if (jobRepository.existsByAccountIdAndPeriodicDedupKey(
                accountId,
                GoogleCalendarOperationJob.PERIODIC_SYNC_DEDUP_KEY
        )) {
            return false;
        }
        saveSyncJob(integration, GoogleCalendarOperationTrigger.PERIODIC);
        return true;
    }

    @Transactional(readOnly = true)
    public java.util.List<Long> connectedAccountIds() {
        return integrationRepository.findAllByState(GoogleCalendarIntegrationState.CONNECTED)
                .stream()
                .map(GoogleCalendarIntegration::getAccountId)
                .toList();
    }

    private GoogleCalendarIntegration connectedIntegrationForUpdate(Long accountId) {
        GoogleCalendarIntegration integration = integrationRepository
                .findByAccountIdForUpdate(accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.GOOGLE_CALENDAR_NOT_CONNECTED));
        if (!integration.isConnected()) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_NOT_CONNECTED);
        }
        return integration;
    }

    private void saveSyncJob(
            GoogleCalendarIntegration integration,
            GoogleCalendarOperationTrigger trigger
    ) {
        Instant now = Instant.now(clock);
        long sequence = integration.allocateOperationSequence();
        jobRepository.save(GoogleCalendarOperationJob.sync(integration, sequence, trigger, now));
        registerAfterCommitWakeup();
    }

    private void registerAfterCommitWakeup() {
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
