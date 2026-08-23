package com.calio.calendar.integration.connection.service;

import com.calio.calendar.external.google.GoogleOAuthClient;
import com.calio.calendar.external.google.GoogleCalendarInvalidGrantException;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.dto.GoogleAccessTokenRefreshResponse;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.security.TokenEncryptor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarAccessTokenService {

    private static final Duration REFRESH_WINDOW = Duration.ofSeconds(60);

    private final GoogleCalendarIntegrationQueryService integrationQueryService;
    private final GoogleCalendarIntegrationCommandService integrationCommandService;
    private final GoogleOAuthClient googleOAuthClient;
    private final TokenEncryptor tokenEncryptor;
    private final GoogleCalendarIntegrationLifecycleService lifecycleService;
    private final Clock clock;

    public GoogleCalendarAccessTokenService(
            GoogleCalendarIntegrationQueryService integrationQueryService,
            GoogleCalendarIntegrationCommandService integrationCommandService,
            GoogleOAuthClient googleOAuthClient,
            TokenEncryptor tokenEncryptor,
            GoogleCalendarIntegrationLifecycleService lifecycleService,
            Clock clock
    ) {
        this.integrationQueryService = integrationQueryService;
        this.integrationCommandService = integrationCommandService;
        this.googleOAuthClient = googleOAuthClient;
        this.tokenEncryptor = tokenEncryptor;
        this.lifecycleService = lifecycleService;
        this.clock = clock;
    }

    public String getAccessToken(Long integrationId) {
        TokenState tokenState = readTokenState(integrationId);
        if (isUsable(tokenState.accessTokenExpiresAt())) {
            return tokenEncryptor.decrypt(tokenState.encryptedAccessToken());
        }
        return refresh(tokenState);
    }

    public String forceRefresh(Long integrationId) {
        return refresh(readTokenState(integrationId));
    }

    private String refresh(TokenState tokenState) {
        String refreshToken = tokenEncryptor.decrypt(tokenState.encryptedRefreshToken());
        GoogleAccessTokenRefreshResponse response;
        try {
            response = googleOAuthClient.refreshAccessToken(refreshToken);
        } catch (GoogleCalendarInvalidGrantException exception) {
            lifecycleService.disconnectAfterInvalidGrant(tokenState.integrationId(), Instant.now(clock));
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED, exception);
        }
        String encryptedAccessToken = tokenEncryptor.encryptAccessToken(response.accessToken());
        Instant expiresAt = Instant.now(clock).plusSeconds(response.expiresIn());
        persistRefreshedToken(tokenState, encryptedAccessToken, expiresAt);
        return response.accessToken();
    }

    private boolean isUsable(Instant expiresAt) {
        return expiresAt.isAfter(Instant.now(clock).plus(REFRESH_WINDOW));
    }

    protected TokenState readTokenState(Long integrationId) {
        GoogleCalendarIntegration integration = integrationQueryService.getIntegrationById(integrationId);
        return new TokenState(
                integration.getId(),
                integration.getEncryptedRefreshToken(),
                integration.getEncryptedAccessToken(),
                integration.getAccessTokenExpiresAt()
        );
    }

    protected void persistRefreshedToken(
            TokenState tokenState,
            String encryptedAccessToken,
            Instant accessTokenExpiresAt
    ) {
        integrationCommandService.replaceAccessToken(
                tokenState.integrationId(),
                tokenState.encryptedRefreshToken(),
                encryptedAccessToken,
                accessTokenExpiresAt
        );
    }

    protected record TokenState(
            Long integrationId,
            String encryptedRefreshToken,
            String encryptedAccessToken,
            Instant accessTokenExpiresAt
    ) {
    }
}
