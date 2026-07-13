package com.calio.calendar.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    AUTH_TOKEN_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication token is required."),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Authentication token is invalid."),
    AUTH_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "Authentication token is revoked."),
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Event not found."),
    RECURRENCE_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Recurrence event not found."),
    RECURRENCE_OCCURRENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Recurrence occurrence not found."),
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "Task not found."),
    COMPLETED_TASK_TITLE_UPDATE_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "Completed task title update is not allowed."
    ),
    TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "Tag not found."),
    DEFAULT_TAG_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "Default tag not found."),
    INVALID_TAG_COLOR_CODE(HttpStatus.BAD_REQUEST, "Invalid tag color code."),
    RECURRENCE_UPDATE_TIME_RANGE_INVALID(HttpStatus.BAD_REQUEST, "Recurrence update time range invalid."),
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "Invalid time range."),
    INVALID_RECURRENCE_DATE_RANGE(HttpStatus.BAD_REQUEST, "Invalid recurrence date range."),
    INVALID_RECURRENCE_TIME_RANGE(HttpStatus.BAD_REQUEST, "Invalid recurrence time range."),
    INVALID_RECURRENCE_FREQUENCY(HttpStatus.BAD_REQUEST, "Invalid recurrence frequency."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed."),

    EXTERNAL_API_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "External API is temporarily unavailable."),
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
