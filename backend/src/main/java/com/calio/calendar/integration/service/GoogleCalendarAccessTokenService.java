package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleOAuthClient;
import com.calio.calendar.external.google.dto.GoogleAccessTokenRefreshResponse;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.security.TokenEncryptor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarAccessTokenService {

    private static final Duration REFRESH_WINDOW = Duration.ofSeconds(60);

    private final GoogleCalendarIntegrationRepository integrationRepository;
    private final GoogleOAuthClient googleOAuthClient;
    private final TokenEncryptor tokenEncryptor;
    private final Clock clock;

    public GoogleCalendarAccessTokenService(
            GoogleCalendarIntegrationRepository integrationRepository,
            GoogleOAuthClient googleOAuthClient,
            TokenEncryptor tokenEncryptor,
            Clock clock
    ) {
        this.integrationRepository = integrationRepository;
        this.googleOAuthClient = googleOAuthClient;
        this.tokenEncryptor = tokenEncryptor;
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
        GoogleAccessTokenRefreshResponse response = googleOAuthClient.refreshAccessToken(refreshToken);
        String encryptedAccessToken = tokenEncryptor.encryptAccessToken(response.accessToken());
        Instant expiresAt = Instant.now(clock).plusSeconds(response.expiresIn());
        persistRefreshedToken(tokenState, encryptedAccessToken, expiresAt);
        return response.accessToken();
    }

    private boolean isUsable(Instant expiresAt) {
        return expiresAt.isAfter(Instant.now(clock).plus(REFRESH_WINDOW));
    }

    protected TokenState readTokenState(Long integrationId) {
        GoogleCalendarIntegration integration = integrationRepository.findById(integrationId)
                .orElseThrow(() -> new CalioException(ErrorCode.GOOGLE_CALENDAR_NOT_CONNECTED));
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
        int updated = integrationRepository.updateAccessToken(
                tokenState.integrationId(),
                tokenState.encryptedRefreshToken(),
                encryptedAccessToken,
                accessTokenExpiresAt
        );
        if (updated != 1) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED);
        }
    }

    protected record TokenState(
            Long integrationId,
            String encryptedRefreshToken,
            String encryptedAccessToken,
            Instant accessTokenExpiresAt
    ) {
    }
}
