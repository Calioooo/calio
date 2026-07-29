package com.calio.calendar.groupspace.controller;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupinvitation.controller.GroupInvitationAcceptanceController;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        GroupMemberController.class,
        GroupInvitationAcceptanceController.class
})
public class LifecycleExceptionHandler {

    @ExceptionHandler(CalioException.class)
    public ResponseEntity<?> handleCalioException(CalioException exception) {
        return response(exception.getErrorCode());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<?> handleValidationException(Exception exception) {
        return response(ErrorCode.VALIDATION_FAILED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpectedException(Exception exception) {
        return ResponseEntity.status(500).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(new LifecycleServerProblemResponse("about:blank", "Internal Server Error", 500));
    }

    private ResponseEntity<?> response(ErrorCode errorCode) {
        if (errorCode.getStatus().is5xxServerError()) {
            return ResponseEntity.status(errorCode.getStatus())
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(new LifecycleServerProblemResponse(
                            "about:blank",
                            errorCode.getStatus().getReasonPhrase(),
                            errorCode.getStatus().value()
                    ));
        }
        return ResponseEntity.status(errorCode.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(LifecycleProblemResponse.clientError(errorCode));
    }

    public record LifecycleServerProblemResponse(String type, String title, int status) {
    }
}
