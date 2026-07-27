package com.calio.calendar.common.error;

public sealed interface ErrorProblemDetail
        permits ErrorProblemDetail.ClientError, ErrorProblemDetail.ServerError {

    String type();

    String title();

    int status();

    static ErrorProblemDetail from(ErrorCode errorCode, String detail) {
        if (errorCode.getStatus().is5xxServerError()) {
            return new ServerError(
                    "about:blank",
                    errorCode.getStatus().getReasonPhrase(),
                    errorCode.getStatus().value()
            );
        }
        return new ClientError(
                "about:blank",
                errorCode.name(),
                errorCode.getStatus().value(),
                detail,
                errorCode.name()
        );
    }

    record ClientError(
            String type,
            String title,
            int status,
            String detail,
            String errorCode
    ) implements ErrorProblemDetail {
    }

    record ServerError(
            String type,
            String title,
            int status
    ) implements ErrorProblemDetail {
    }
}
