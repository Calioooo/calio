package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.domain.GoogleOperationJob;
import com.calio.calendar.integration.service.GoogleOperationJobPersistenceService.GoogleOperationOwnershipLostException;
import jakarta.annotation.PreDestroy;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;

@Service
public class GoogleOperationWorker {

    private static final Logger log = LoggerFactory.getLogger(GoogleOperationWorker.class);
    private static final String UNSUPPORTED_JOB_KIND = "UNSUPPORTED_JOB_KIND";
    private static final long SHUTDOWN_GRACE_SECONDS = 5L;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Set<Long> activeAccounts = ConcurrentHashMap.newKeySet();
    private final GoogleOperationJobPersistenceService jobPersistenceService;
    private final GoogleCalendarSyncService syncService;

    public GoogleOperationWorker(
            GoogleOperationJobPersistenceService jobPersistenceService,
            GoogleCalendarSyncService syncService
    ) {
        this.jobPersistenceService = jobPersistenceService;
        this.syncService = syncService;
    }

    public void wake(Long accountId) {
        if (!activeAccounts.add(accountId)) {
            return;
        }
        try {
            executor.execute(() -> runAccount(accountId));
        } catch (RejectedExecutionException exception) {
            activeAccounts.remove(accountId);
            log.warn("Google operation wake-up was rejected. accountId={}", accountId);
        }
    }

    private void runAccount(Long accountId) {
        String workerToken = UUID.randomUUID().toString();
        try {
            if (!jobPersistenceService.acquireLease(accountId, workerToken)) {
                return;
            }
            while (processHead(accountId, workerToken)
                    == JobExecutionResult.CONTINUE_WITH_NEXT_JOB) {
                // Drain the ordered Account queue while ownership remains valid.
            }
        } catch (RuntimeException exception) {
            log.warn("Google operation worker failed. accountId={} causeType={}",
                    accountId, exception.getClass().getSimpleName());
        } finally {
            try {
                jobPersistenceService.releaseLease(accountId, workerToken);
            } finally {
                activeAccounts.remove(accountId);
            }
        }
    }

    private JobExecutionResult processHead(Long accountId, String workerToken) {
        GoogleOperationJob job = jobPersistenceService.claimHead(accountId, workerToken);
        if (job == null) {
            return JobExecutionResult.STOP_ACCOUNT_PROCESSING;
        }
        if (!GoogleOperationJob.SYNC_KIND.equals(job.getKind())) {
            if (jobPersistenceService.skipIfConflicted(job, workerToken)) {
                return JobExecutionResult.CONTINUE_WITH_NEXT_JOB;
            }
            jobPersistenceService.terminate(
                    job.getId(), job.getAccountId(), workerToken, UNSUPPORTED_JOB_KIND);
            return JobExecutionResult.CONTINUE_WITH_NEXT_JOB;
        }
        return executeSync(job, workerToken);
    }

    private JobExecutionResult executeSync(GoogleOperationJob job, String workerToken) {
        try {
            syncService.executeOwned(job.getId(), job.getAccountId(), workerToken);
            return JobExecutionResult.CONTINUE_WITH_NEXT_JOB;
        } catch (RuntimeException failure) {
            return persistFailure(job, workerToken, classifyFailure(failure));
        }
    }

    private JobExecutionResult persistFailure(
            GoogleOperationJob job,
            String workerToken,
            FailureClassification classification
    ) {
        return switch (classification.disposition()) {
            case ABANDON -> JobExecutionResult.STOP_ACCOUNT_PROCESSING;
            case RETRY -> {
                jobPersistenceService.retry(job, workerToken, classification.reason());
                yield JobExecutionResult.STOP_ACCOUNT_PROCESSING;
            }
            case TERMINAL -> {
                jobPersistenceService.terminate(
                        job.getId(), job.getAccountId(), workerToken, classification.reason());
                yield JobExecutionResult.CONTINUE_WITH_NEXT_JOB;
            }
        };
    }

    private FailureClassification classifyFailure(RuntimeException failure) {
        if (failure instanceof GoogleOperationOwnershipLostException) {
            return FailureClassification.abandon();
        }
        if (failure instanceof CalioException calioException) {
            return classifyCalioFailure(calioException);
        }
        if (failure instanceof TransientDataAccessException) {
            return FailureClassification.retry(failure.getClass().getSimpleName());
        }
        if (isTransientDatabaseFailure(failure)) {
            return FailureClassification.retry(rootCauseType(failure));
        }
        return FailureClassification.terminal(ErrorCode.INTERNAL_SERVER_ERROR.name());
    }

    private FailureClassification classifyCalioFailure(CalioException failure) {
        ErrorCode errorCode = failure.getErrorCode();
        return isPermanent(errorCode)
                ? FailureClassification.terminal(errorCode.name())
                : FailureClassification.retry(causalReason(failure));
    }

    private String causalReason(CalioException failure) {
        if (failure.getErrorCode() != ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED
                || failure.getCause() == null) {
            return failure.getErrorCode().name();
        }
        return failure.getErrorCode().name() + ":" + rootCauseType(failure);
    }

    private boolean isTransientDatabaseFailure(Throwable failure) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof SQLTransientException
                    || cause instanceof SQLRecoverableException
                    || cause instanceof TransientDataAccessException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private String rootCauseType(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }

    private boolean isPermanent(ErrorCode errorCode) {
        return errorCode == ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED
                || errorCode == ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID
                || errorCode == ErrorCode.GOOGLE_CALENDAR_SYNC_TOKEN_MISSING;
    }

    private enum JobExecutionResult {
        CONTINUE_WITH_NEXT_JOB,
        STOP_ACCOUNT_PROCESSING
    }

    private enum FailureDisposition {
        ABANDON,
        RETRY,
        TERMINAL
    }

    private record FailureClassification(FailureDisposition disposition, String reason) {

        private static FailureClassification abandon() {
            return new FailureClassification(FailureDisposition.ABANDON, null);
        }

        private static FailureClassification retry(String reason) {
            return new FailureClassification(FailureDisposition.RETRY, reason);
        }

        private static FailureClassification terminal(String reason) {
            return new FailureClassification(FailureDisposition.TERMINAL, reason);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
