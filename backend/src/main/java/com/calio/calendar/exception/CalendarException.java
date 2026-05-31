package com.calio.calendar.exception;

public class CalendarException extends RuntimeException {

    private final ErrorCode errorCode;

    public CalendarException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
