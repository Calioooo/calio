package com.calio.calendar.common.exception;

public class CalendarException extends RuntimeException {

    private final ErrorCode errorCode;

    public CalendarException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
