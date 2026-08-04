package com.calio.calendar.integration.service;

import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.domain.GoogleOperationJob;
import com.calio.calendar.integration.repository.GoogleOperationJobRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleOperationJobPersistenceService {

    private static final Logger log =
            LoggerFactory.getLogger(GoogleOperationJobPersistenceService.class);
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(10), Duration.ofMinutes(30), Duration.ofHours(1), Duration.ofHours(6));

    private final GoogleCalendarIntegrationRepository integrationRepository;
    private final GoogleOperationJobRepository jobRepository;
    private final Clock clock;

    public GoogleOperationJobPersistenceService(
            GoogleCalendarIntegrationRepository integrationRepository,
            GoogleOperationJobRepository jobRepository,
            Clock clock
    ) {
        this.integrationRepository = integrationRepository;
        this.jobRepository = jobRepository;
        this.clock = clock;
    }

    @Transactional
    public boolean acquireLease(Long accountId, String workerToken) {
        boolean acquired = integrationRepository.acquireGoogleOperationLease(accountId, workerToken) == 1;
        if (acquired) {
            log.info("Google operation lease acquired. accountId={} state=ACQUIRED", accountId);
        } else {
            log.warn("Google operation lease acquisition failed. accountId={} state=NOT_ACQUIRED", accountId);
        }
        return acquired;
    }

    @Transactional
    public GoogleOperationJob claimHead(Long accountId, String workerToken) {
        GoogleOperationJob head = jobRepository.findAccountHeadForUpdate(accountId).orElse(null);
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
        head.claim(workerToken);
        log.info("Google operation job claimed. accountId={} jobId={} state={} previousState={}",
                accountId, head.getId(), head.getState().name(), previousState);
        return head;
    }

    @Transactional
    public void renewAndAssertOwned(Long jobId, Long accountId, String workerToken) {
        if (integrationRepository.renewOwnedGoogleOperationLease(
                jobId, accountId, workerToken) != 1) {
            log.warn("Google operation ownership lost. accountId={} jobId={} state=PROCESSING", accountId, jobId);
            throw new GoogleOperationOwnershipLostException();
        }
    }

    @Transactional
    public void retry(GoogleOperationJob job, String workerToken, String reason) {
        Duration delay = RETRY_DELAYS.get(Math.min(job.getRetryCount(), RETRY_DELAYS.size() - 1));
        if (jobRepository.retry(job.getId(), workerToken, Instant.now(clock).plus(delay), reason) != 1) {
            log.warn("Google operation retry transition rejected. accountId={} jobId={} state=PENDING delaySeconds={}",
                    job.getAccountId(), job.getId(), delay.toSeconds());
            throw new GoogleOperationOwnershipLostException();
        }
        log.info("Google operation job scheduled for retry. accountId={} jobId={} state=PENDING delaySeconds={}",
                job.getAccountId(), job.getId(), delay.toSeconds());
    }

    @Transactional
    public void terminate(Long jobId, Long accountId, String workerToken, String reason) {
        if (jobRepository.terminateWithSyncError(jobId, workerToken, reason) != 1) {
            log.warn("Google operation terminal transition rejected. accountId={} jobId={} state=SYNC_ERROR",
                    accountId, jobId);
            throw new GoogleOperationOwnershipLostException();
        }
        log.info("Google operation job terminated. accountId={} jobId={} state=SYNC_ERROR", accountId, jobId);
    }

    @Transactional
    public void succeed(Long jobId, Long accountId, String workerToken) {
        if (jobRepository.deleteOwnedSuccessful(jobId, workerToken) != 1) {
            log.warn("Google operation success deletion rejected. accountId={} jobId={} state=PROCESSING",
                    accountId, jobId);
            throw new GoogleOperationOwnershipLostException();
        }
        log.info("Google operation job deleted after success. accountId={} jobId={} state=PROCESSING transition=DELETE",
                accountId, jobId);
    }

    @Transactional
    public void releaseLease(Long accountId, String workerToken) {
        int released = integrationRepository.releaseGoogleOperationLease(accountId, workerToken);
        if (released == 1) {
            log.info("Google operation lease released. accountId={} state=RELEASED", accountId);
        } else {
            log.warn("Google operation lease release skipped. accountId={} state=NOT_OWNED", accountId);
        }
    }

    @Transactional(readOnly = true)
    public List<Long> findRecoverableAccountIds() {
        return jobRepository.findRecoverableAccountIds(Instant.now(clock));
    }

    @Transactional
    public int deleteTerminalBatch(Instant cutoff) {
        List<Long> ids = jobRepository.findTerminalIdsBefore(cutoff, PageRequest.of(0, 500));
        jobRepository.deleteAllByIdInBatch(ids);
        return ids.size();
    }

    public static final class GoogleOperationOwnershipLostException extends RuntimeException {
    }
}
