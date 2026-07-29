package com.calio.calendar.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CalioException.class)
    public ResponseEntity<ProblemDetail> handleCalioException(
            CalioException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        logCalioException(errorCode, exception, request);
        return toResponse(errorCode, exception.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ProblemDetail> handleValidationException(
            Exception exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        log.debug(
                "API validation failed. errorCode={} method={}",
                errorCode.name(),
                request.getMethod()
        );
        return toResponse(errorCode, errorCode.getDefaultMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        log.error(
                "Unhandled API exception. status={} errorCode={} method={}",
                errorCode.getStatus().value(),
                errorCode.name(),
                request.getMethod(),
                exception
        );
        return toResponse(errorCode, errorCode.getDefaultMessage());
    }

    private ResponseEntity<ProblemDetail> toResponse(ErrorCode errorCode, String detail) {
        return ResponseEntity
                .status(errorCode.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ErrorProblemDetail.from(errorCode, detail));
    }

    private void logCalioException(
            ErrorCode errorCode,
            CalioException exception,
            HttpServletRequest request
    ) {
        if (errorCode.getStatus().is5xxServerError()) {
            log.error(
                    "API error. status={} errorCode={} method={}",
                    errorCode.getStatus().value(),
                    errorCode.name(),
                    request.getMethod(),
                    exception
            );
            return;
        }

        log.warn(
                "API error. status={} errorCode={} method={}",
                errorCode.getStatus().value(),
                errorCode.name(),
                request.getMethod()
        );
    }
}
