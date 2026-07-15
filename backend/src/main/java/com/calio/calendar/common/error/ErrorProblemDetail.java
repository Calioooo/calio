package com.calio.calendar.common.error;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class ErrorProblemDetail {

    private ErrorProblemDetail() {
    }

    public static ProblemDetail from(ErrorCode errorCode, String detail) {
        if (errorCode.getStatus().is5xxServerError()) {
            return fromServerError(errorCode.getStatus());
        }

        ProblemDetail problemDetail = ProblemDetail.forStatus(errorCode.getStatus());
        problemDetail.setType(URI.create("about:blank"));
        problemDetail.setTitle(errorCode.name());
        problemDetail.setDetail(detail);
        problemDetail.setProperty("errorCode", errorCode.name());
        return problemDetail;
    }

    private static ProblemDetail fromServerError(HttpStatus status) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setType(URI.create("about:blank"));
        problemDetail.setTitle(status.getReasonPhrase());
        return problemDetail;
    }
}
