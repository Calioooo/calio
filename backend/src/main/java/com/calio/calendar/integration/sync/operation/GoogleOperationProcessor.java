package com.calio.calendar.integration.sync.operation;

import com.calio.calendar.integration.sync.GoogleCalendarSyncService;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJob;
import com.calio.calendar.integration.sync.operation.dto.GoogleOperationFailureDecision;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GoogleOperationProcessor {

    private static final String UNSUPPORTED_JOB_KIND = "UNSUPPORTED_JOB_KIND";

    private final GoogleOperationJobService jobService;
    private final GoogleOperationLeaseService operationLeaseService;
    private final GoogleCalendarSyncService syncService;
    private final GoogleOperationFailureClassifier failureClassifier;

    public GoogleOperationProcessor(
            GoogleOperationJobService jobService,
            GoogleOperationLeaseService operationLeaseService,
            GoogleCalendarSyncService syncService,
            GoogleOperationFailureClassifier failureClassifier
    ) {
        this.jobService = jobService;
        this.operationLeaseService = operationLeaseService;
        this.syncService = syncService;
        this.failureClassifier = failureClassifier;
    }

    public void processAccount(Long accountId) {
        String workerToken = UUID.randomUUID().toString();
        try {
            if (!operationLeaseService.acquire(accountId, workerToken)) {
                return;
            }
            while (processHead(accountId, workerToken)
                    == JobExecutionResult.CONTINUE_WITH_NEXT_JOB) {
                // Drain the ordered Account queue while ownership remains valid.
            }
        } finally {
            operationLeaseService.release(accountId, workerToken);
        }
    }

    private JobExecutionResult processHead(Long accountId, String workerToken) {
        GoogleOperationJob job = jobService.claimNextJob(accountId, workerToken);
        if (job == null) {
            return JobExecutionResult.STOP_ACCOUNT_PROCESSING;
        }
        if (!GoogleOperationJob.SYNC_KIND.equals(job.getKind())) {
            jobService.terminate(
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

            GoogleOperationFailureDecision failureDecision = failureClassifier.classify(failure);

            persistFailureAction(
                    job,
                    workerToken,
                    failureDecision
            );

            return mapExecutionResult(failureDecision);
        }
    }

    private void persistFailureAction(
            GoogleOperationJob job,
            String workerToken,
            GoogleOperationFailureDecision decision
    ) {
        switch (decision.action()) {
            case RETRY -> jobService.retry(job, workerToken, decision.reason());
            case FAIL -> jobService.terminate(
                    job.getId(),
                    job.getAccountId(),
                    workerToken,
                    decision.reason()
            );
        }
    }

    private JobExecutionResult mapExecutionResult(
            GoogleOperationFailureDecision decision
    ) {
        return switch (decision.action()) {
            case SKIP -> JobExecutionResult.STOP_ACCOUNT_PROCESSING;
            case RETRY -> JobExecutionResult.STOP_ACCOUNT_PROCESSING;
            case FAIL -> JobExecutionResult.CONTINUE_WITH_NEXT_JOB;
        };
    }

    private enum JobExecutionResult {
        CONTINUE_WITH_NEXT_JOB,
        STOP_ACCOUNT_PROCESSING
    }
}
