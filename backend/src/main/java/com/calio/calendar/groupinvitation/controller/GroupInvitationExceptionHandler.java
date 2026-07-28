package com.calio.calendar.groupinvitation.controller;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        GroupInvitationController.class,
        GroupInvitationPreviewController.class
})
public class GroupInvitationExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GroupInvitationExceptionHandler.class);

    @ExceptionHandler(CalioException.class)
    public ResponseEntity<InvitationProblemResponse> handleCalioException(
            CalioException exception
    ) {
        if (exception.getErrorCode().getStatus().is5xxServerError()) {
            log.error(
                    "Group invitation API error. status={} errorCode={}",
                    exception.getErrorCode().getStatus().value(),
                    exception.getErrorCode().name()
            );
        }
        return toResponse(exception.getErrorCode());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<InvitationProblemResponse> handleValidationException() {
        return toResponse(ErrorCode.VALIDATION_FAILED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<InvitationProblemResponse> handleUnexpectedException() {
        log.error(
                "Unhandled group invitation API error. status={} errorCode={}",
                ErrorCode.INTERNAL_SERVER_ERROR.getStatus().value(),
                ErrorCode.INTERNAL_SERVER_ERROR.name()
        );
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<InvitationProblemResponse> toResponse(ErrorCode errorCode) {
        InvitationProblemResponse body = errorCode.getStatus().is5xxServerError()
                ? InvitationProblemResponse.serverError(errorCode.getStatus())
                : InvitationProblemResponse.clientError(errorCode);
        return ResponseEntity
                .status(errorCode.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InvitationProblemResponse(
            URI type,
            String title,
            int status,
            String detail,
            String errorCode
    ) {

        static InvitationProblemResponse clientError(ErrorCode errorCode) {
            return new InvitationProblemResponse(
                    URI.create("about:blank"),
                    errorCode.name(),
                    errorCode.getStatus().value(),
                    errorCode.getDefaultMessage(),
                    errorCode.name()
            );
        }

        static InvitationProblemResponse serverError(HttpStatus status) {
            return new InvitationProblemResponse(
                    URI.create("about:blank"),
                    status.getReasonPhrase(),
                    status.value(),
                    null,
                    null
            );
        }
    }
}
