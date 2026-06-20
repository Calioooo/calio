package com.calio.calendar.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Event not found."),
    RECURRENCE_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Recurrence event not found."),
    RECURRENCE_EVENT_UPDATE_FORBIDDEN(HttpStatus.FORBIDDEN, "Recurrence event update forbidden."),
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "Invalid time range."),
    INVALID_RECURRENCE_DATE_RANGE(HttpStatus.BAD_REQUEST, "Invalid recurrence date range."),
    INVALID_RECURRENCE_TIME_RANGE(HttpStatus.BAD_REQUEST, "Invalid recurrence time range."),
    INVALID_RECURRENCE_FREQUENCY(HttpStatus.BAD_REQUEST, "Invalid recurrence frequency."),
    RECURRENCE_UPDATE_BODY_NOT_OBJECT(HttpStatus.BAD_REQUEST, "Recurrence update body must be a JSON object."),
    RECURRENCE_UPDATE_UNSUPPORTED_FIELD(HttpStatus.BAD_REQUEST, "Recurrence update contains unsupported field."),
    RECURRENCE_UPDATE_TITLE_BLANK(HttpStatus.BAD_REQUEST, "Recurrence update title must not be blank."),
    RECURRENCE_UPDATE_START_AT_REQUIRED(HttpStatus.BAD_REQUEST, "Recurrence update startAt is required."),
    RECURRENCE_UPDATE_START_AT_INVALID(HttpStatus.BAD_REQUEST, "Recurrence update startAt is invalid."),
    RECURRENCE_UPDATE_END_AT_REQUIRED(HttpStatus.BAD_REQUEST, "Recurrence update endAt is required."),
    RECURRENCE_UPDATE_END_AT_INVALID(HttpStatus.BAD_REQUEST, "Recurrence update endAt is invalid."),
    RECURRENCE_UPDATE_TIME_RANGE_INVALID(HttpStatus.BAD_REQUEST, "Recurrence update time range is invalid."),
    RECURRENCE_UPDATE_FREQUENCY_REQUIRED(HttpStatus.BAD_REQUEST, "Recurrence update recurrenceFrequency is required."),
    RECURRENCE_UPDATE_FREQUENCY_INVALID(HttpStatus.BAD_REQUEST, "Recurrence update recurrenceFrequency is invalid."),
    RECURRENCE_UPDATE_SCOPE_UNSUPPORTED(HttpStatus.BAD_REQUEST, "Recurrence update scope is unsupported."),
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
