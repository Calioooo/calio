package com.calio.calendar.integration.sync.operation;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.sync.operation.dto.GoogleOperationFailureDecision;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

@Component
public class GoogleOperationFailureClassifier {

    public GoogleOperationFailureDecision classify(RuntimeException failure) {
        if (failure instanceof GoogleOperationOwnershipLostException) {
            return GoogleOperationFailureDecision.skip();
        }
        if (failure instanceof CalioException calioException) {
            return classifyCalioFailure(calioException);
        }
        if (failure instanceof TransientDataAccessException) {
            return GoogleOperationFailureDecision.retry(
                    failure.getClass().getSimpleName()
            );
        }
        if (isRetryableDatabaseFailure(failure)) {
            return GoogleOperationFailureDecision.retry(rootCauseType(failure));
        }
        return GoogleOperationFailureDecision.fail(
                ErrorCode.INTERNAL_SERVER_ERROR.name()
        );
    }

    private GoogleOperationFailureDecision classifyCalioFailure(CalioException failure) {
        ErrorCode errorCode = failure.getErrorCode();
        return isNonRetryable(errorCode)
                ? GoogleOperationFailureDecision.fail(errorCode.name())
                : GoogleOperationFailureDecision.retry(failureReason(failure));
    }

    private String failureReason(CalioException failure) {
        if (failure.getErrorCode() != ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED
                || failure.getCause() == null) {
            return failure.getErrorCode().name();
        }
        return failure.getErrorCode().name() + ":" + rootCauseType(failure);
    }

    private boolean isRetryableDatabaseFailure(Throwable failure) {
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

    private boolean isNonRetryable(ErrorCode errorCode) {
        return errorCode == ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED
                || errorCode == ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID
                || errorCode == ErrorCode.GOOGLE_CALENDAR_SYNC_TOKEN_MISSING;
    }
}
