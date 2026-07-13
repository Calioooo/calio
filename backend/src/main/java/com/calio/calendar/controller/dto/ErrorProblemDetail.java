package com.calio.calendar.controller.dto;

import com.calio.calendar.exception.ErrorCode;
import java.net.URI;
import org.springframework.http.ProblemDetail;

public final class ErrorProblemDetail {

    private ErrorProblemDetail() {
    }

    public static ProblemDetail from(ErrorCode errorCode, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(errorCode.getStatus());
        problemDetail.setType(URI.create("about:blank"));
        problemDetail.setTitle(errorCode.name());
        problemDetail.setDetail(detail);
        problemDetail.setProperty("errorCode", errorCode.name());
        return problemDetail;
    }
}
