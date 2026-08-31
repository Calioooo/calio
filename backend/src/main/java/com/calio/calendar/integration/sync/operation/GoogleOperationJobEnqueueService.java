package com.calio.calendar.integration.sync.operation;

import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.connection.service.GoogleCalendarConnectionCommandService;
import com.calio.calendar.integration.connection.service.GoogleCalendarIntegrationCommandService;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJobTrigger;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class GoogleOperationJobEnqueueService {

    private final GoogleCalendarConnectionCommandService connectionCommandService;
    private final GoogleCalendarIntegrationCommandService integrationCommandService;
    private final GoogleOperationJobCommandService jobCommandService;
    private final GoogleOperationWorker worker;
    private final Clock clock;

    public GoogleOperationJobEnqueueService(
            GoogleCalendarConnectionCommandService connectionCommandService,
            GoogleCalendarIntegrationCommandService integrationCommandService,
            GoogleOperationJobCommandService jobCommandService,
            GoogleOperationWorker worker,
            Clock clock
    ) {
        this.connectionCommandService = connectionCommandService;
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
        connectionCommandService.lockConnectedConnection(accountId);
        GoogleCalendarIntegration integration = integrationCommandService.tryLockIntegration(accountId)
                .orElseThrow();
        GoogleOperationJob job = GoogleOperationJob.sync(
                UUID.randomUUID().toString(),
                integration.getId(),
                accountId,
                integration.allocateGoogleOperationSequence(),
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
