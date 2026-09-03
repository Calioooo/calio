package com.calio.calendar.integration.sync.operation.scheduler;

import com.calio.calendar.integration.connection.service.GoogleCalendarConnectionQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobService;
import com.calio.calendar.integration.sync.operation.GoogleOperationWorker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GoogleOperationScheduler {

    private static final Logger log = LoggerFactory.getLogger(GoogleOperationScheduler.class);
    private static final int PERIODIC_ENQUEUE_BATCH_SIZE = 500;
    private static final int TERMINAL_CLEANUP_BATCH_SIZE = 500;
    private static final long FIRST_ACCOUNT_ID = 0L;

    private final GoogleCalendarConnectionQueryService connectionQueryService;
    private final GoogleOperationJobEnqueueService enqueueService;
    private final GoogleOperationJobService jobService;
    private final GoogleOperationWorker worker;
    private final Clock clock;

    public GoogleOperationScheduler(
            GoogleCalendarConnectionQueryService connectionQueryService,
            GoogleOperationJobEnqueueService enqueueService,
            GoogleOperationJobService jobService,
            GoogleOperationWorker worker,
            Clock clock
    ) {
        this.connectionQueryService = connectionQueryService;
        this.enqueueService = enqueueService;
        this.jobService = jobService;
        this.worker = worker;
        this.clock = clock;
    }

    @Scheduled(cron = "0 */10 * * * *")
    public void recoverAndEnqueuePeriodicSyncs() {
        jobService.findRecoverableAccountIds().forEach(worker::wake);
        enqueuePeriodicSyncsInBatches();
    }

    private void enqueuePeriodicSyncsInBatches() {
        long lastAccountId = FIRST_ACCOUNT_ID;
        while (true) {
            List<Long> accountIds = connectionQueryService.listConnectedAccountIds(
                    lastAccountId,
                    PERIODIC_ENQUEUE_BATCH_SIZE
            );
            accountIds.forEach(this::enqueuePeriodicSafely);
            if (accountIds.size() < PERIODIC_ENQUEUE_BATCH_SIZE) {
                return;
            }
            lastAccountId = accountIds.getLast();
        }
    }

    private void enqueuePeriodicSafely(Long accountId) {
        try {
            enqueueService.enqueuePeriodicSync(accountId);
        } catch (DataIntegrityViolationException exception) {
            log.debug("Periodic Google sync Job already exists. accountId={}", accountId);
        } catch (RuntimeException exception) {
            log.error("Failed to enqueue periodic Google sync Job. accountId={}", accountId, exception);
        }
    }

    @Scheduled(cron = "0 40 4 * * *", zone = "Asia/Seoul")
    public void cleanTerminalJobs() {
        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(30));
        while (jobService.deleteTerminalBatch(cutoff) == TERMINAL_CLEANUP_BATCH_SIZE) {
            // Keep the fixed cutoff while each batch commits independently.
        }
    }
}
