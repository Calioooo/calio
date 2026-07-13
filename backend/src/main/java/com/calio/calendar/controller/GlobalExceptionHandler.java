package com.calio.calendar.controller;

import com.calio.calendar.controller.dto.ErrorProblemDetail;
import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import jakarta.validation.ConstraintViolationException;
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

    @ExceptionHandler(CalioException.class)
    public ResponseEntity<ProblemDetail> handleCalioException(CalioException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return toResponse(errorCode, exception.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ProblemDetail> handleValidationException(Exception exception) {
        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        return toResponse(errorCode, errorCode.getDefaultMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedException(Exception exception) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return toResponse(errorCode, errorCode.getDefaultMessage());
    }

    private ResponseEntity<ProblemDetail> toResponse(ErrorCode errorCode, String detail) {
        return ResponseEntity
                .status(errorCode.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ErrorProblemDetail.from(errorCode, detail));
    }
}
