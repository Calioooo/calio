package com.calio.calendar.integration.sync.operation;

import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJob;
import com.calio.calendar.integration.sync.operation.repository.GoogleOperationJobRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GoogleOperationJobCommandService {

    private final GoogleOperationJobRepository jobRepository;

    public GoogleOperationJobCommandService(GoogleOperationJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public Optional<GoogleOperationJob> tryLockNextOperationJob(Long accountId) {
        return jobRepository.findAccountHeadForUpdate(accountId);
    }

    public GoogleOperationJob enqueueOperationJob(GoogleOperationJob job) {
        return jobRepository.saveAndFlush(job);
    }

    public void claimOperationJob(GoogleOperationJob job, String ownerToken) {
        job.claim(ownerToken);
    }

    public void retryOperationJob(
            GoogleOperationJob job,
            String ownerToken,
            Instant runnableAt,
            String reason
    ) {
        if (jobRepository.retry(job.getId(), ownerToken, runnableAt, reason) != 1) {
            throw new GoogleOperationOwnershipLostException();
        }
    }

    public void failOperationJob(Long jobId, String ownerToken, String reason) {
        if (jobRepository.terminateWithSyncError(jobId, ownerToken, reason) != 1) {
            throw new GoogleOperationOwnershipLostException();
        }
    }

    public void completeOperationJob(Long jobId, String ownerToken) {
        if (jobRepository.deleteOwnedSuccessful(jobId, ownerToken) != 1) {
            throw new GoogleOperationOwnershipLostException();
        }
    }

    public void deleteJobsForIntegration(Long integrationId) {
        jobRepository.deleteByIntegrationId(integrationId);
    }

    public void deleteOperationJobs(Collection<Long> jobIds) {
        jobRepository.deleteAllByIdInBatch(jobIds);
    }
}
