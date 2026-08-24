package com.calio.calendar.integration.sync.operation;

import com.calio.calendar.integration.sync.GoogleCalendarSyncService;
import com.calio.calendar.integration.connection.service.GoogleCalendarIntegrationCommandService;
import com.calio.calendar.external.google.GoogleCalendarInvalidGrantException;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJob;
import com.calio.calendar.integration.sync.operation.dto.GoogleOperationFailureDecision;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GoogleOperationProcessor {

    private static final String UNSUPPORTED_JOB_KIND = "UNSUPPORTED_JOB_KIND";

    private final GoogleOperationJobService jobService;
    private final GoogleOperationLeaseService operationLeaseService;
    private final GoogleCalendarSyncService syncService;
    private final GoogleOperationFailureClassifier failureClassifier;
    private final GoogleCalendarIntegrationCommandService integrationCommandService;
    private final Clock clock;

    public GoogleOperationProcessor(
            GoogleOperationJobService jobService,
            GoogleOperationLeaseService operationLeaseService,
            GoogleCalendarSyncService syncService,
            GoogleOperationFailureClassifier failureClassifier,
            GoogleCalendarIntegrationCommandService integrationCommandService,
            Clock clock
    ) {
        this.jobService = jobService;
        this.operationLeaseService = operationLeaseService;
        this.syncService = syncService;
        this.failureClassifier = failureClassifier;
        this.integrationCommandService = integrationCommandService;
        this.clock = clock;
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
            return handleSyncFailure(job, workerToken, failure);
        }
    }

    private JobExecutionResult handleSyncFailure(
            GoogleOperationJob job,
            String workerToken,
            RuntimeException failure
    ) {
        if (isAlreadyDisconnectedAfterInvalidGrant(failure)) {
            return JobExecutionResult.STOP_ACCOUNT_PROCESSING;
        }
        GoogleOperationFailureDecision failureDecision = failureClassifier.classify(failure);
        persistFailureAction(job, workerToken, failureDecision);
        if (!requiresIntegrationPause(failure)) {
            return mapExecutionResult(failureDecision);
        }
        integrationCommandService.markConnectedIntegrationSyncError(
                job.getAccountId(),
                ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED.name(),
                Instant.now(clock)
        );
        return JobExecutionResult.STOP_ACCOUNT_PROCESSING;
    }

    private boolean requiresIntegrationPause(RuntimeException failure) {
        return failure instanceof CalioException calioException
                && calioException.getErrorCode() == ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED;
    }

    private boolean isAlreadyDisconnectedAfterInvalidGrant(RuntimeException failure) {
        return failure instanceof CalioException calioException
                && calioException.getCause() instanceof GoogleCalendarInvalidGrantException;
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
