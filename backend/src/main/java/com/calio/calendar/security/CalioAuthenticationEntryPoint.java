package com.calio.calendar.security;

import com.calio.calendar.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class CalioAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuthenticationErrorResponseWriter errorResponseWriter;

    public CalioAuthenticationEntryPoint(AuthenticationErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        errorResponseWriter.write(response, ErrorCode.AUTH_TOKEN_REQUIRED);
    }
}
