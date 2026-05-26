package com.calio.calendar.controller.dto;

import com.calio.calendar.exception.ErrorCode;

public record ErrorResponse(
        String errorCode,
        String message
) {

    public static ErrorResponse from(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message);
    }
}
