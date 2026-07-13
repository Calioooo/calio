package com.calio.calendar.security;

import com.calio.calendar.controller.dto.ErrorProblemDetail;
import com.calio.calendar.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class AuthenticationErrorResponseWriter {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationErrorResponseWriter.class);

    private final ObjectMapper objectMapper;

    public AuthenticationErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        log.warn(
                "Authentication failed. status={} errorCode={} method={} path={}",
                errorCode.getStatus().value(),
                errorCode.name(),
                request.getMethod(),
                request.getRequestURI()
        );
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ErrorProblemDetail.from(errorCode, errorCode.getDefaultMessage())
        ));
    }
}
