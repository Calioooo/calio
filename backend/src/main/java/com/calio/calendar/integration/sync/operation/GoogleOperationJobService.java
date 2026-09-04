package com.calio.calendar.integration.sync.operation;

import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJob;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleOperationJobService {

    private static final Logger log =
            LoggerFactory.getLogger(GoogleOperationJobService.class);
    private static final int RECOVERY_BATCH_SIZE = 500;
    private static final int TERMINAL_CLEANUP_BATCH_SIZE = 500;
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(10), Duration.ofMinutes(30), Duration.ofHours(1), Duration.ofHours(6));

    private final GoogleOperationJobQueryService jobQueryService;
    private final GoogleOperationJobCommandService jobCommandService;
    private final Clock clock;

    public GoogleOperationJobService(
            GoogleOperationJobQueryService jobQueryService,
            GoogleOperationJobCommandService jobCommandService,
            Clock clock
    ) {
        this.jobQueryService = jobQueryService;
        this.jobCommandService = jobCommandService;
        this.clock = clock;
    }

    @Transactional
    public GoogleOperationJob claimNextJob(Long accountId, Long integrationId, String workerToken) {
        GoogleOperationJob head = jobCommandService.tryLockNextOperationJob(integrationId).orElse(null);
        if (head == null) {
            log.debug("Google operation head not found. accountId={} state=EMPTY", accountId);
            return null;
        }
        Instant now = Instant.now(clock);
        if (!head.canBeClaimedAt(now)) {
            long delaySeconds = Duration.between(now, head.getRunnableAt()).toSeconds();
            log.info("Google operation head is not runnable. accountId={} jobId={} state={} delaySeconds={}",
                    accountId, head.getId(), head.getState().name(), delaySeconds);
            return null;
        }
        String previousState = head.getState().name();
        jobCommandService.claimOperationJob(head, workerToken);
        log.info("Google operation job claimed. accountId={} jobId={} state={} previousState={}",
                accountId, head.getId(), head.getState().name(), previousState);
        return head;
    }

    @Transactional
    public void retry(GoogleOperationJob job, String workerToken, String reason) {
        Duration delay = getDelayByRetryCount(job);
        try {
            jobCommandService.retryOperationJob(
                    job,
                    workerToken,
                    Instant.now(clock).plus(delay),
                    reason
            );
        } catch (GoogleOperationOwnershipLostException exception) {
            log.warn("Google operation retry transition rejected. accountId={} jobId={} state=PENDING delaySeconds={}",
                    job.getAccountId(), job.getId(), delay.toSeconds());
            throw exception;
        }
        log.info("Google operation job scheduled for retry. accountId={} jobId={} state=PENDING delaySeconds={}",
                job.getAccountId(), job.getId(), delay.toSeconds());
    }

    @Transactional
    public void terminate(Long jobId, Long accountId, String workerToken, String reason) {
        try {
            jobCommandService.failOperationJob(jobId, workerToken, reason);
        } catch (GoogleOperationOwnershipLostException exception) {
            log.warn("Google operation terminal transition rejected. accountId={} jobId={} state=SYNC_ERROR",
                    accountId, jobId);
            throw exception;
        }
        log.info("Google operation job terminated. accountId={} jobId={} state=SYNC_ERROR", accountId, jobId);
    }

    @Transactional
    public void succeed(Long jobId, Long accountId, String workerToken) {
        try {
            jobCommandService.completeOperationJob(jobId, workerToken);
        } catch (GoogleOperationOwnershipLostException exception) {
            log.warn("Google operation success deletion rejected. accountId={} jobId={} state=PROCESSING",
                    accountId, jobId);
            throw exception;
        }
        log.info("Google operation job deleted after success. accountId={} jobId={} state=PROCESSING transition=DELETE",
                accountId, jobId);
    }

    @Transactional
    public void completeSyncRun(Long jobId, Long accountId, String workerToken) {
        try {
            jobCommandService.completeSyncOperationJob(jobId, workerToken);
        } catch (GoogleOperationOwnershipLostException exception) {
            log.warn("Google sync final transition rejected. accountId={} jobId={}", accountId, jobId);
            throw exception;
        }
        log.info("Google sync run completed. accountId={} jobId={}", accountId, jobId);
    }

    @Transactional
    public void recordSyncConflict(Long jobId, Long accountId, String workerToken) {
        jobCommandService.markConflictDetected(jobId, workerToken);
        log.info("Google sync conflict detected. accountId={} jobId={}", accountId, jobId);
    }

    @Transactional
    public void skipConflictedScope(Long jobId, Long accountId, String workerToken) {
        jobCommandService.skipConflictedScope(jobId, workerToken);
        log.info("Google operation skipped because mapping scope is conflicted. accountId={} jobId={}",
                accountId, jobId);
    }

    @Transactional(readOnly = true)
    public List<Long> findRecoverableAccountIds() {
        return jobQueryService.listRecoverableAccountIds(
                Instant.now(clock),
                RECOVERY_BATCH_SIZE
        );
    }

    @Transactional
    public int deleteTerminalBatch(Instant cutoff) {
        List<Long> ids = jobQueryService.listExpiredTerminalJobIds(
                cutoff,
                TERMINAL_CLEANUP_BATCH_SIZE
        );
        jobCommandService.deleteOperationJobs(ids);
        return ids.size();
    }

    private Duration getDelayByRetryCount(GoogleOperationJob job) {
        return RETRY_DELAYS.get(Math.min(job.getRetryCount(), RETRY_DELAYS.size() - 1));
    }
}
