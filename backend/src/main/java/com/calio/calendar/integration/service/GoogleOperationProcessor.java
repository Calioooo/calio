package com.calio.calendar.integration.service;

import com.calio.calendar.integration.domain.GoogleOperationJob;
import com.calio.calendar.integration.service.dto.GoogleOperationFailureDecision;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GoogleOperationProcessor {

    private static final String UNSUPPORTED_JOB_KIND = "UNSUPPORTED_JOB_KIND";

    private final GoogleOperationJobPersistenceService jobPersistenceService;
    private final GoogleCalendarSyncService syncService;
    private final GoogleOperationFailureClassifier failureClassifier;

    public GoogleOperationProcessor(
            GoogleOperationJobPersistenceService jobPersistenceService,
            GoogleCalendarSyncService syncService,
            GoogleOperationFailureClassifier failureClassifier
    ) {
        this.jobPersistenceService = jobPersistenceService;
        this.syncService = syncService;
        this.failureClassifier = failureClassifier;
    }

    public void processAccount(Long accountId) {
        String workerToken = UUID.randomUUID().toString();
        try {
            if (!jobPersistenceService.acquireLease(accountId, workerToken)) {
                return;
            }
            while (processHead(accountId, workerToken)
                    == JobExecutionResult.CONTINUE_WITH_NEXT_JOB) {
                // Drain the ordered Account queue while ownership remains valid.
            }
        } finally {
            jobPersistenceService.releaseLease(accountId, workerToken);
        }
    }

    private JobExecutionResult processHead(Long accountId, String workerToken) {
        GoogleOperationJob job = jobPersistenceService.claimNextJob(accountId, workerToken);
        if (job == null) {
            return JobExecutionResult.STOP_ACCOUNT_PROCESSING;
        }
        if (!GoogleOperationJob.SYNC_KIND.equals(job.getKind())) {
            jobPersistenceService.terminate(
                    job.getId(),
                    job.getAccountId(),
                    workerToken,
                    UNSUPPORTED_JOB_KIND
            );
            return JobExecutionResult.CONTINUE_WITH_NEXT_JOB;
        }
        return executeSync(job, workerToken);
    }

    private JobExecutionResult executeSync(GoogleOperationJob job, String workerToken) {
        try {
            syncService.synchronize(job.getId(), job.getAccountId(), workerToken);
            return JobExecutionResult.CONTINUE_WITH_NEXT_JOB;
        } catch (RuntimeException failure) {
            return persistFailure(
                    job,
                    workerToken,
                    failureClassifier.classify(failure)
            );
        }
    }

    private JobExecutionResult persistFailure(
            GoogleOperationJob job,
            String workerToken,
            GoogleOperationFailureDecision decision
    ) {
        return switch (decision.action()) {
            case ABANDON -> JobExecutionResult.STOP_ACCOUNT_PROCESSING;
            case RETRY -> {
                jobPersistenceService.retry(job, workerToken, decision.reason());
                yield JobExecutionResult.STOP_ACCOUNT_PROCESSING;
            }
            case TERMINAL -> {
                jobPersistenceService.terminate(
                        job.getId(),
                        job.getAccountId(),
                        workerToken,
                        decision.reason()
                );
                yield JobExecutionResult.CONTINUE_WITH_NEXT_JOB;
            }
        };
    }

    private enum JobExecutionResult {
        CONTINUE_WITH_NEXT_JOB,
        STOP_ACCOUNT_PROCESSING
    }
}
