package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.domain.GoogleCalendarOperationKind;
import com.calio.calendar.integration.domain.GoogleCalendarOperationStatus;
import com.calio.calendar.integration.service.GoogleCalendarOperationCoordinator.ClaimedOperation;
import com.calio.calendar.external.google.GoogleOAuthInvalidGrantException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarOperationWorker {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarOperationWorker.class);

    private final GoogleCalendarOperationCoordinator coordinator;
    private final Map<GoogleCalendarOperationKind, GoogleCalendarOperationHandler> handlers;

    public GoogleCalendarOperationWorker(
            GoogleCalendarOperationCoordinator coordinator,
            List<GoogleCalendarOperationHandler> handlers
    ) {
        this.coordinator = coordinator;
        this.handlers = new EnumMap<>(GoogleCalendarOperationKind.class);
        handlers.forEach(handler -> this.handlers.put(handler.kind(), handler));
    }

    public void processAccount(Long accountId) {
        coordinator.claim(accountId, UUID.randomUUID().toString()).ifPresent(this::perform);
    }

    private void perform(ClaimedOperation operation) {
        GoogleCalendarOperationHandler handler = handlers.get(operation.kind());
        if (handler == null) {
            terminateInvariantFailure(operation, "UNSUPPORTED_OPERATION_KIND");
            return;
        }
        try {
            GoogleCalendarOperationResult result = handler.perform(operation);
            if (result.conflictDetected()) {
                coordinator.terminateConflict(operation, result.nextSyncToken());
            } else {
                coordinator.complete(operation, result.nextSyncToken());
            }
        } catch (StaleGoogleCalendarOperationOwnerException staleOwner) {
            log.info("Ignored stale Google operation outcome. operationKind={}", operation.kind());
        } catch (GoogleOAuthInvalidGrantException invalidGrant) {
            disconnectAfterInvalidGrant(operation);
        } catch (CalioException failure) {
            applyKnownFailure(operation, failure);
        } catch (RuntimeException failure) {
            log.warn(
                    "Google operation failed unexpectedly. operationKind={} causeType={}",
                    operation.kind(),
                    failure.getClass().getSimpleName()
            );
            safeRetry(operation);
        }
    }

    private void disconnectAfterInvalidGrant(ClaimedOperation operation) {
        try {
            coordinator.disconnectAfterInvalidGrant(operation);
        } catch (StaleGoogleCalendarOperationOwnerException ignored) {
            log.info("Ignored stale invalid_grant outcome. operationKind={}", operation.kind());
        }
    }

    private void applyKnownFailure(ClaimedOperation operation, CalioException failure) {
        ErrorCode errorCode = failure.getErrorCode();
        if (errorCode == ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID
                || errorCode == ErrorCode.GOOGLE_CALENDAR_SYNC_TOKEN_MISSING) {
            terminateInvariantFailure(operation, errorCode.name());
            return;
        }
        safeRetry(operation);
    }

    private void terminateInvariantFailure(ClaimedOperation operation, String reason) {
        try {
            coordinator.terminate(
                    operation,
                    GoogleCalendarOperationStatus.SYNC_ERROR,
                    reason,
                    true
            );
        } catch (StaleGoogleCalendarOperationOwnerException ignored) {
            log.info("Ignored stale Google operation terminal outcome. reason={}", reason);
        }
    }

    private void safeRetry(ClaimedOperation operation) {
        try {
            coordinator.retry(operation);
        } catch (StaleGoogleCalendarOperationOwnerException ignored) {
            log.info("Ignored stale Google operation retry outcome. operationKind={}", operation.kind());
        }
    }
}
