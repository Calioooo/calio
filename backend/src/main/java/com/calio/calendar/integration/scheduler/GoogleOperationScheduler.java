package com.calio.calendar.integration.scheduler;

import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.service.GoogleOperationJobEnqueueService;
import com.calio.calendar.integration.service.GoogleOperationJobPersistenceService;
import com.calio.calendar.integration.service.GoogleOperationWorker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GoogleOperationScheduler {

    private final GoogleCalendarIntegrationRepository integrationRepository;
    private final GoogleOperationJobEnqueueService enqueueService;
    private final GoogleOperationJobPersistenceService persistenceService;
    private final GoogleOperationWorker worker;
    private final Clock clock;

    public GoogleOperationScheduler(
            GoogleCalendarIntegrationRepository integrationRepository,
            GoogleOperationJobEnqueueService enqueueService,
            GoogleOperationJobPersistenceService persistenceService,
            GoogleOperationWorker worker,
            Clock clock
    ) {
        this.integrationRepository = integrationRepository;
        this.enqueueService = enqueueService;
        this.persistenceService = persistenceService;
        this.worker = worker;
        this.clock = clock;
    }

    @Scheduled(cron = "0 */10 * * * *")
    public void recoverAndEnqueuePeriodicSyncs() {
        persistenceService.findRecoverableAccountIds().forEach(worker::wake);
        integrationRepository.findAllConnectedAccountIds().forEach(this::enqueuePeriodicSafely);
    }

    private void enqueuePeriodicSafely(Long accountId) {
        try {
            enqueueService.enqueuePeriodicSync(accountId);
        } catch (DataIntegrityViolationException ignored) {
            // The database uniqueness guarantee resolves scheduler races.
        }
    }

    @Scheduled(cron = "0 40 4 * * *", zone = "Asia/Seoul")
    public void cleanTerminalJobs() {
        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(30));
        while (persistenceService.deleteTerminalBatch(cutoff) == 500) {
            // Keep the fixed cutoff while each batch commits independently.
        }
    }
}
