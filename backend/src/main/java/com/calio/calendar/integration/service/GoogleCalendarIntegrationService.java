package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleOAuthClient;
import com.calio.calendar.external.google.GoogleOAuthProperties;
import com.calio.calendar.external.google.dto.GoogleTokenResponse;
import com.calio.calendar.external.google.dto.GoogleUserInfoResponse;
import com.calio.calendar.integration.controller.dto.GoogleCalendarConnectRequest;
import com.calio.calendar.integration.controller.dto.GoogleCalendarIntegrationResponse;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.security.TokenEncryptor;
import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarIntegrationService {

    private final GoogleOAuthProperties googleOAuthProperties;
    private final GoogleOAuthClient googleOAuthClient;
    private final TokenEncryptor tokenEncryptor;
    private final GoogleCalendarIntegrationPersistenceService persistenceService;
    private final Clock clock;

    public GoogleCalendarIntegrationService(
            GoogleOAuthProperties googleOAuthProperties,
            GoogleOAuthClient googleOAuthClient,
            TokenEncryptor tokenEncryptor,
            GoogleCalendarIntegrationPersistenceService persistenceService,
            Clock clock
    ) {
        this.googleOAuthProperties = googleOAuthProperties;
        this.googleOAuthClient = googleOAuthClient;
        this.tokenEncryptor = tokenEncryptor;
        this.persistenceService = persistenceService;
        this.clock = clock;
    }

    public GoogleCalendarIntegrationResponse connect(Long accountId, GoogleCalendarConnectRequest request) {
        validateAuthorizationCode(request.authorizationCode());
        validateConfiguration();

        Instant tokenReceivedAt = Instant.now(clock);
        GoogleTokenResponse tokenResponse = googleOAuthClient.exchangeAuthorizationCode(request.authorizationCode());
        GoogleUserInfoResponse userInfo = googleOAuthClient.fetchUserInfo(tokenResponse.accessToken());
        EncryptedGoogleTokens encryptedTokens = encryptTokens(tokenResponse);
        Instant accessTokenExpiresAt = tokenReceivedAt.plusSeconds(tokenResponse.expiresIn());

        GoogleCalendarIntegration integration = saveOrReplace(
                accountId,
                userInfo,
                encryptedTokens,
                accessTokenExpiresAt,
                tokenReceivedAt
        );
        return GoogleCalendarIntegrationResponse.connected(integration);
    }

    public GoogleCalendarIntegrationResponse getConnection(Long accountId) {
        GoogleCalendarIntegration integration = persistenceService.findByAccountIdOrNull(accountId);
        if (integration == null) {
            return GoogleCalendarIntegrationResponse.disconnected();
        }

        return GoogleCalendarIntegrationResponse.from(integration);
    }

    public void disconnect(Long accountId) {
        persistenceService.deleteByAccountId(accountId);
    }

    private void validateConfiguration() {
        if (!googleOAuthProperties.isConfigured()) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_CONFIGURATION_MISSING);
        }
        tokenEncryptor.validateConfigured();
    }

    private void validateAuthorizationCode(String authorizationCode) {
        if (authorizationCode != null && !authorizationCode.isBlank()) {
            return;
        }

        throw new CalioException(ErrorCode.GOOGLE_CALENDAR_AUTHORIZATION_CODE_REQUIRED);
    }

    private EncryptedGoogleTokens encryptTokens(GoogleTokenResponse tokenResponse) {
        return new EncryptedGoogleTokens(
                tokenEncryptor.encrypt(tokenResponse.refreshToken()),
                tokenEncryptor.encrypt(tokenResponse.accessToken())
        );
    }

    private GoogleCalendarIntegration saveOrReplace(
            Long accountId,
            GoogleUserInfoResponse userInfo,
            EncryptedGoogleTokens encryptedTokens,
            Instant accessTokenExpiresAt,
            Instant connectedAt
    ) {
        try {
            return persist(accountId, userInfo, encryptedTokens, accessTokenExpiresAt, connectedAt);
        } catch (DataIntegrityViolationException exception) {
            return retryAfterUniqueConstraintRace(accountId, userInfo, encryptedTokens, accessTokenExpiresAt, connectedAt);
        }
    }

    private GoogleCalendarIntegration retryAfterUniqueConstraintRace(
            Long accountId,
            GoogleUserInfoResponse userInfo,
            EncryptedGoogleTokens encryptedTokens,
            Instant accessTokenExpiresAt,
            Instant connectedAt
    ) {
        try {
            return persist(accountId, userInfo, encryptedTokens, accessTokenExpiresAt, connectedAt);
        } catch (DataIntegrityViolationException exception) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_INTEGRATION_SAVE_FAILED, exception);
        }
    }

    private GoogleCalendarIntegration persist(
            Long accountId,
            GoogleUserInfoResponse userInfo,
            EncryptedGoogleTokens encryptedTokens,
            Instant accessTokenExpiresAt,
            Instant connectedAt
    ) {
        return persistenceService.saveOrReplace(
                accountId,
                userInfo.subject(),
                userInfo.email(),
                encryptedTokens.refreshToken(),
                encryptedTokens.accessToken(),
                accessTokenExpiresAt,
                connectedAt
        );
    }

    private record EncryptedGoogleTokens(
            String refreshToken,
            String accessToken
    ) {
    }
}
