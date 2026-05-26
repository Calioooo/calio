package com.calio.calendar.exception;

public class InvalidTimeRangeException extends BusinessException {

    public InvalidTimeRangeException(String message) {
        super(ErrorCode.INVALID_TIME_RANGE, message);
    }
}
