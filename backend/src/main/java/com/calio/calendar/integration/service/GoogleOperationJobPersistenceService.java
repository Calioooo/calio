package com.calio.calendar.integration.service;

import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.domain.GoogleOperationJob;
import com.calio.calendar.integration.repository.GoogleOperationJobRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleOperationJobPersistenceService {

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
        return integrationRepository.acquireGoogleOperationLease(accountId, workerToken) == 1;
    }

    @Transactional
    public GoogleOperationJob claimHead(Long accountId, String workerToken) {
        GoogleOperationJob head = jobRepository.findAccountHeadForUpdate(accountId).orElse(null);
        if (head == null) {
            return null;
        }
        if (!head.canBeClaimedAt(Instant.now(clock))) {
            return null;
        }
        head.claim(workerToken);
        return head;
    }

    @Transactional
    public void renewAndAssertOwned(Long jobId, Long accountId, String workerToken) {
        if (integrationRepository.renewOwnedGoogleOperationLease(
                jobId, accountId, workerToken) != 1) {
            throw new GoogleOperationOwnershipLostException();
        }
    }

    @Transactional
    public void retry(GoogleOperationJob job, String workerToken, String reason) {
        Duration delay = RETRY_DELAYS.get(Math.min(job.getRetryCount(), RETRY_DELAYS.size() - 1));
        if (jobRepository.retry(job.getId(), workerToken, Instant.now(clock).plus(delay), reason) != 1) {
            throw new GoogleOperationOwnershipLostException();
        }
    }

    @Transactional
    public void terminate(Long jobId, String workerToken, String reason) {
        if (jobRepository.terminateWithSyncError(jobId, workerToken, reason) != 1) {
            throw new GoogleOperationOwnershipLostException();
        }
    }

    @Transactional
    public void succeed(Long jobId, String workerToken) {
        if (jobRepository.deleteOwnedSuccessful(jobId, workerToken) != 1) {
            throw new GoogleOperationOwnershipLostException();
        }
    }

    @Transactional
    public void releaseLease(Long accountId, String workerToken) {
        integrationRepository.releaseGoogleOperationLease(accountId, workerToken);
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
