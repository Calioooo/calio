package com.calio.calendar.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Event not found."),
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "Invalid time range."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
