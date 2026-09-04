package com.calio.calendar.integration.sync.operation;

import com.calio.calendar.integration.connection.service.GoogleCalendarConnectionCommandService;
import com.calio.calendar.integration.connection.service.GoogleCalendarIntegrationQueryService;
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

    private static final String UNSUPPORTED_JOB_SCOPE = "UNSUPPORTED_JOB_SCOPE";

    private final GoogleOperationJobService jobService;
    private final GoogleOperationLeaseService operationLeaseService;
    private final GoogleOperationJobHandlerRegistry handlerRegistry;
    private final GoogleOperationFailureClassifier failureClassifier;
    private final GoogleCalendarConnectionCommandService connectionCommandService;
    private final GoogleCalendarIntegrationQueryService integrationQueryService;
    private final Clock clock;

    public GoogleOperationProcessor(
            GoogleOperationJobService jobService,
            GoogleOperationLeaseService operationLeaseService,
            GoogleOperationJobHandlerRegistry handlerRegistry,
            GoogleOperationFailureClassifier failureClassifier,
            GoogleCalendarConnectionCommandService connectionCommandService,
            GoogleCalendarIntegrationQueryService integrationQueryService,
            Clock clock
    ) {
        this.jobService = jobService;
        this.operationLeaseService = operationLeaseService;
        this.handlerRegistry = handlerRegistry;
        this.failureClassifier = failureClassifier;
        this.connectionCommandService = connectionCommandService;
        this.integrationQueryService = integrationQueryService;
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
        Long integrationId = integrationQueryService.getIntegrationIfExists(accountId)
                .orElseThrow()
                .getId();
        GoogleOperationJob job = jobService.claimNextJob(accountId, integrationId, workerToken);
        if (job == null) {
            return JobExecutionResult.STOP_ACCOUNT_PROCESSING;
        }
        return execute(job, workerToken);
    }

    private JobExecutionResult terminateUnsupported(
            GoogleOperationJob job,
            String workerToken,
            String reason
    ) {
        jobService.terminate(job.getId(), job.getAccountId(), workerToken, reason);
        return JobExecutionResult.CONTINUE_WITH_NEXT_JOB;
    }

    private JobExecutionResult execute(GoogleOperationJob job, String workerToken) {
        try {
            handlerRegistry.execute(job, workerToken);
            return JobExecutionResult.CONTINUE_WITH_NEXT_JOB;
        } catch (GoogleOperationJobHandlerNotFoundException exception) {
            return terminateUnsupported(job, workerToken, UNSUPPORTED_JOB_SCOPE);
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
        connectionCommandService.markConnectedConnectionSyncError(
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
