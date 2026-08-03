package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.domain.GoogleOperationJob;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;

@Service
public class GoogleOperationWorker {

    private static final Logger log = LoggerFactory.getLogger(GoogleOperationWorker.class);
    private static final String UNSUPPORTED_JOB_KIND = "UNSUPPORTED_JOB_KIND";
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
            while (processHead(accountId, workerToken)) {
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

    private boolean processHead(Long accountId, String workerToken) {
        GoogleOperationJob job = jobPersistenceService.claimHead(accountId, workerToken);
        if (job == null) {
            return false;
        }
        if (!GoogleOperationJob.SYNC_KIND.equals(job.getKind())) {
            jobPersistenceService.terminate(job.getId(), workerToken, UNSUPPORTED_JOB_KIND);
            return true;
        }
        return executeSync(job, workerToken);
    }

    private boolean executeSync(GoogleOperationJob job, String workerToken) {
        try {
            syncService.executeOwned(job.getId(), job.getAccountId(), workerToken);
            return true;
        } catch (GoogleOperationJobPersistenceService.StaleGoogleOperationWorkerException ignored) {
            // Expiry or ownership loss abandons the attempt for durable recovery.
            return false;
        } catch (CalioException exception) {
            return persistClassifiedFailure(job, workerToken, exception);
        } catch (TransientDataAccessException exception) {
            jobPersistenceService.retry(job, workerToken, exception.getClass().getSimpleName());
            return false;
        } catch (RuntimeException exception) {
            if (isTransientDatabaseFailure(exception)) {
                jobPersistenceService.retry(job, workerToken, rootCauseType(exception));
                return false;
            }
            jobPersistenceService.terminate(job.getId(), workerToken, ErrorCode.INTERNAL_SERVER_ERROR.name());
            return true;
        }
    }

    private boolean persistClassifiedFailure(
            GoogleOperationJob job,
            String workerToken,
            CalioException failure
    ) {
        ErrorCode errorCode = failure.getErrorCode();
        if (isPermanent(errorCode)) {
            jobPersistenceService.terminate(job.getId(), workerToken, errorCode.name());
            return true;
        }
        jobPersistenceService.retry(job, workerToken, causalReason(failure));
        return false;
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

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
