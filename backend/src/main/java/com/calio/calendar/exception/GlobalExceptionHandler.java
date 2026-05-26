package com.calio.calendar.exception;

import com.calio.calendar.controller.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String DEFAULT_VALIDATION_MESSAGE = "Request validation failed";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        return buildResponse(exception.getErrorCode(), exception.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            BindException.class
    })
    public ResponseEntity<ErrorResponse> handleValidationException(Exception exception) {
        return buildResponse(ErrorCode.VALIDATION_FAILED, resolveValidationMessage(exception));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("Unexpected internal server error", exception);
        return buildResponse(ErrorCode.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private ResponseEntity<ErrorResponse> buildResponse(ErrorCode errorCode, String message) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponse.from(errorCode, message));
    }

    private String resolveValidationMessage(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            if (methodArgumentNotValidException.getBindingResult().hasFieldErrors()) {
                return methodArgumentNotValidException.getBindingResult().getFieldErrors().getFirst().getDefaultMessage();
            }

            if (methodArgumentNotValidException.getBindingResult().hasGlobalErrors()) {
                return methodArgumentNotValidException.getBindingResult().getGlobalErrors().getFirst().getDefaultMessage();
            }
        }

        if (exception instanceof MissingServletRequestParameterException missingServletRequestParameterException) {
            return missingServletRequestParameterException.getParameterName() + " is required";
        }

        if (exception instanceof MethodArgumentTypeMismatchException methodArgumentTypeMismatchException) {
            return methodArgumentTypeMismatchException.getName() + " has invalid format";
        }

        if (exception instanceof HttpMessageNotReadableException) {
            return DEFAULT_VALIDATION_MESSAGE;
        }

        if (exception instanceof BindException bindException && bindException.hasFieldErrors()) {
            return bindException.getFieldErrors().getFirst().getDefaultMessage();
        }

        return DEFAULT_VALIDATION_MESSAGE;
    }
}
