package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.domain.GoogleOperationJob;
import com.calio.calendar.integration.domain.GoogleOperationJobTrigger;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.repository.GoogleOperationJobRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class GoogleOperationJobEnqueueService {

    private final GoogleCalendarIntegrationRepository integrationRepository;
    private final GoogleOperationJobRepository jobRepository;
    private final GoogleOperationWorker worker;
    private final Clock clock;

    public GoogleOperationJobEnqueueService(
            GoogleCalendarIntegrationRepository integrationRepository,
            GoogleOperationJobRepository jobRepository,
            GoogleOperationWorker worker,
            Clock clock
    ) {
        this.integrationRepository = integrationRepository;
        this.jobRepository = jobRepository;
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
        GoogleCalendarIntegration integration = integrationRepository.findByAccountIdForUpdate(accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.GOOGLE_CALENDAR_NOT_CONNECTED));
        GoogleOperationJob job = GoogleOperationJob.sync(
                UUID.randomUUID().toString(),
                integration.getId(),
                accountId,
                integration.allocateGoogleOperationSequence(),
                trigger,
                Instant.now(clock)
        );
        jobRepository.saveAndFlush(job);
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
