package com.calio.calendar.integration.service;

import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.domain.GoogleOperationJob;
import com.calio.calendar.integration.domain.GoogleOperationJobTrigger;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class GoogleOperationJobEnqueueService {

    private final GoogleCalendarIntegrationCommandService integrationCommandService;
    private final GoogleOperationJobCommandService jobCommandService;
    private final GoogleOperationWorker worker;
    private final Clock clock;

    public GoogleOperationJobEnqueueService(
            GoogleCalendarIntegrationCommandService integrationCommandService,
            GoogleOperationJobCommandService jobCommandService,
            GoogleOperationWorker worker,
            Clock clock
    ) {
        this.integrationCommandService = integrationCommandService;
        this.jobCommandService = jobCommandService;
        this.worker = worker;
        this.clock = clock;
    }

    @Transactional
    public void enqueueManualSync(Long accountId) {
        enqueueSync(accountId, GoogleOperationJobTrigger.MANUAL);
    }

    @Transactional
    public void enqueuePeriodicSync(Long accountId) {
        enqueueSync(accountId, GoogleOperationJobTrigger.PERIODIC);
    }

    private void enqueueSync(Long accountId, GoogleOperationJobTrigger trigger) {
        GoogleCalendarIntegration integration = integrationCommandService.lockIntegration(accountId);
        GoogleOperationJob job = GoogleOperationJob.sync(
                UUID.randomUUID().toString(),
                integration.getId(),
                accountId,
                integrationCommandService.allocateOperationSequence(integration),
                trigger,
                Instant.now(clock)
        );
        jobCommandService.enqueueOperationJob(job);
        wakeAfterCommit(accountId);
    }

    private void wakeAfterCommit(Long accountId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                worker.wake(accountId);
            }
        });
    }
}
