package com.calio.calendar.security;

import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccountTokenAuthenticationService accountTokenAuthenticationService;
    private final AuthenticationErrorResponseWriter errorResponseWriter;

    public BearerTokenAuthenticationFilter(
            AccountTokenAuthenticationService accountTokenAuthenticationService,
            AuthenticationErrorResponseWriter errorResponseWriter
    ) {
        this.accountTokenAuthenticationService = accountTokenAuthenticationService;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean authenticated = authenticateWithBearerToken(authorizationHeader, request, response);
        if (!authenticated) {
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean authenticateWithBearerToken(
            String authorizationHeader,
            HttpServletRequest request,
            HttpServletResponse response
    )
            throws IOException {
        try {
            String rawToken = extractBearerToken(authorizationHeader);
            AuthenticatedAccount principal = accountTokenAuthenticationService.authenticate(rawToken);
            SecurityContextHolder.getContext().setAuthentication(toAuthentication(principal));
            return true;
        } catch (CalioException exception) {
            SecurityContextHolder.clearContext();
            errorResponseWriter.write(request, response, exception.getErrorCode());
            return false;
        }
    }

    private String extractBearerToken(String authorizationHeader) {
        if (!hasBearerPrefix(authorizationHeader)) {
            throw new CalioException(ErrorCode.AUTH_TOKEN_INVALID);
        }

        String rawToken = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (rawToken.isEmpty()) {
            throw new CalioException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return rawToken;
    }

    private boolean hasBearerPrefix(String authorizationHeader) {
        return authorizationHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length());
    }

    private UsernamePasswordAuthenticationToken toAuthentication(AuthenticatedAccount principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }
}
