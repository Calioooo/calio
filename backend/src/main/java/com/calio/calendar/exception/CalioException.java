package com.calio.calendar.exception;

public class CalioException extends RuntimeException {

    private final ErrorCode errorCode;

    public CalioException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
