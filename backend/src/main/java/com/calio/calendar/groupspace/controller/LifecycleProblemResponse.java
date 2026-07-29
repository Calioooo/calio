package com.calio.calendar.groupspace.controller;

import com.calio.calendar.common.error.ErrorCode;

record LifecycleProblemResponse(
        String type,
        String title,
        int status,
        String detail,
        String errorCode
) {

    static LifecycleProblemResponse clientError(ErrorCode errorCode) {
        return new LifecycleProblemResponse(
                "about:blank",
                errorCode.name(),
                errorCode.getStatus().value(),
                errorCode.getDefaultMessage(),
                errorCode.name()
        );
    }
}
