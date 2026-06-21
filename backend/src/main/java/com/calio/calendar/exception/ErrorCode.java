package com.calio.calendar.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Event not found."),
    RECURRENCE_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Recurrence event not found."),
    RECURRENCE_EVENT_UPDATE_FORBIDDEN(HttpStatus.FORBIDDEN, "Recurrence event update forbidden."),
    RECURRENCE_UPDATE_TIME_RANGE_INVALID(HttpStatus.BAD_REQUEST, "Recurrence update time range invalid."),
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "Invalid time range."),
    INVALID_RECURRENCE_DATE_RANGE(HttpStatus.BAD_REQUEST, "Invalid recurrence date range."),
    INVALID_RECURRENCE_TIME_RANGE(HttpStatus.BAD_REQUEST, "Invalid recurrence time range."),
    INVALID_RECURRENCE_FREQUENCY(HttpStatus.BAD_REQUEST, "Invalid recurrence frequency."),
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
