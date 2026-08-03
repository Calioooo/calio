package com.calio.calendar.integration.service;

import com.calio.calendar.integration.domain.GoogleCalendarOperationJob;
import com.calio.calendar.integration.domain.GoogleCalendarOperationStatus;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.repository.GoogleCalendarOperationJobRepository;
import com.calio.calendar.integration.service.GoogleCalendarRetryPolicy.RetrySchedule;
import com.calio.calendar.integration.service.GoogleCalendarSyncLeaseService.SyncLease;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.calio.calendar.common.error.CalioException;

@Service
public class GoogleCalendarOperationCoordinator {

    private static final List<GoogleCalendarOperationStatus> ACTIVE_STATUSES = List.of(
            GoogleCalendarOperationStatus.PENDING,
            GoogleCalendarOperationStatus.PROCESSING
    );

    private final GoogleCalendarOperationJobRepository jobRepository;
    private final GoogleCalendarIntegrationRepository integrationRepository;
    private final GoogleCalendarSyncLeaseService leaseService;
    private final Clock clock;

    public GoogleCalendarOperationCoordinator(
            GoogleCalendarOperationJobRepository jobRepository,
            GoogleCalendarIntegrationRepository integrationRepository,
            GoogleCalendarSyncLeaseService leaseService,
            Clock clock
    ) {
        this.jobRepository = jobRepository;
        this.integrationRepository = integrationRepository;
        this.leaseService = leaseService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Long> findRunnableAccountIds(int limit) {
        return jobRepository.findRunnableAccountIds(
                Instant.now(clock),
                ACTIVE_STATUSES,
                PageRequest.of(0, limit)
        );
    }

    @Transactional
    public Optional<ClaimedOperation> claim(Long accountId, String ownerToken) {
        SyncLease lease;
        try {
            lease = leaseService.acquire(accountId, ownerToken);
        } catch (CalioException unavailable) {
            return Optional.empty();
        }
        List<GoogleCalendarOperationJob> heads = jobRepository.findAccountHeadForUpdate(
                accountId,
                ACTIVE_STATUSES,
                PageRequest.of(0, 1)
        );
        if (heads.isEmpty()) {
            leaseService.release(lease);
            return Optional.empty();
        }
        GoogleCalendarOperationJob head = heads.getFirst();
        Instant now = Instant.now(clock);
        if (head.getStatus() == GoogleCalendarOperationStatus.PENDING
                && head.getRunnableAt().isAfter(now)) {
            leaseService.release(lease);
            return Optional.empty();
        }
        if (head.getStatus() == GoogleCalendarOperationStatus.PROCESSING) {
            head.retryAt(now, head.getRetryTier());
        }
        head.claim(ownerToken);
        return Optional.of(new ClaimedOperation(head.getId(), head.getOperationKind(), lease));
    }

    @Transactional
    public void complete(ClaimedOperation claimed, String nextSyncToken) {
        GoogleCalendarOperationJob job = ownedJob(claimed);
        commitCursor(claimed, nextSyncToken);
        jobRepository.delete(job);
        leaseService.release(claimed.lease());
    }

    @Transactional
    public void terminateConflict(ClaimedOperation claimed, String nextSyncToken) {
        GoogleCalendarOperationJob job = ownedJob(claimed);
        commitCursor(claimed, nextSyncToken);
        job.terminate(
                GoogleCalendarOperationStatus.CONFLICTED,
                "SEMANTIC_CONFLICT",
                Instant.now(clock)
        );
        jobRepository.save(job);
        leaseService.release(claimed.lease());
    }

    @Transactional
    public void retry(ClaimedOperation claimed) {
        GoogleCalendarOperationJob job = ownedJob(claimed);
        RetrySchedule retry = GoogleCalendarRetryPolicy.next(job.getRetryTier());
        job.retryAt(Instant.now(clock).plus(retry.delay()), retry.tier());
        leaseService.release(claimed.lease());
    }

    @Transactional
    public void terminate(
            ClaimedOperation claimed,
            GoogleCalendarOperationStatus status,
            String reason,
            boolean integrationError
    ) {
        GoogleCalendarOperationJob job = ownedJob(claimed);
        job.terminate(status, reason, Instant.now(clock));
        if (integrationError) {
            job.getIntegration().markSyncError();
        } else {
            leaseService.release(claimed.lease());
        }
    }

    @Transactional
    public void disconnectAfterInvalidGrant(ClaimedOperation claimed) {
        GoogleCalendarOperationJob job = ownedJob(claimed);
        jobRepository.deleteAllByIntegrationId(job.getIntegration().getId());
        job.getIntegration().disconnect(Instant.now(clock));
    }

    private GoogleCalendarOperationJob ownedJob(ClaimedOperation claimed) {
        SyncLease lease = claimed.lease();
        if (!integrationRepository.ownsActiveLease(
                lease.integrationId(),
                lease.runId(),
                Instant.now(clock)
        )) {
            throw new StaleGoogleCalendarOperationOwnerException();
        }
        GoogleCalendarOperationJob job = jobRepository.findByIdForUpdate(claimed.jobId())
                .orElseThrow(StaleGoogleCalendarOperationOwnerException::new);
        if (job.getStatus() != GoogleCalendarOperationStatus.PROCESSING
                || !lease.runId().equals(job.getOwnerToken())) {
            throw new StaleGoogleCalendarOperationOwnerException();
        }
        return job;
    }

    private void commitCursor(ClaimedOperation claimed, String nextSyncToken) {
        if (nextSyncToken == null) {
            return;
        }
        SyncLease lease = claimed.lease();
        if (integrationRepository.finalizeSync(
                lease.integrationId(), lease.runId(), nextSyncToken
        ) != 1) {
            throw new StaleGoogleCalendarOperationOwnerException();
        }
    }

    public record ClaimedOperation(
            Long jobId,
            com.calio.calendar.integration.domain.GoogleCalendarOperationKind kind,
            SyncLease lease
    ) {
    }
}
