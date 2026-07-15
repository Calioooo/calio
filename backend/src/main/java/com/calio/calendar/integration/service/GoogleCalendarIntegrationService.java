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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarIntegrationService.class);

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

    public GoogleCalendarIntegrationResponse getConnectionStatus(Long accountId) {
        GoogleCalendarIntegration integration = persistenceService.findByAccountIdOrNull(accountId);
        if (integration == null) {
            return GoogleCalendarIntegrationResponse.disconnected();
        }

        return GoogleCalendarIntegrationResponse.from(integration);
    }

    public void disconnect(Long accountId) {
        GoogleCalendarIntegration integration = persistenceService.findByAccountIdOrNull(accountId);
        if (integration == null) {
            return;
        }

        String refreshToken = tokenEncryptor.decrypt(integration.getEncryptedRefreshToken());
        googleOAuthClient.revokeToken(refreshToken);
        persistenceService.deleteByAccountId(accountId);
    }

    private void validateConfiguration() {
        if (!googleOAuthProperties.isConfigured()) {
            logIntegrationFailure("validateConfiguration", ErrorCode.GOOGLE_CALENDAR_CONFIGURATION_MISSING);
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_CONFIGURATION_MISSING);
        }
    }

    private EncryptedGoogleTokens encryptTokens(GoogleTokenResponse tokenResponse) {
        return new EncryptedGoogleTokens(
                tokenEncryptor.encryptRefreshToken(tokenResponse.refreshToken()),
                tokenEncryptor.encryptAccessToken(tokenResponse.accessToken())
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
            logIntegrationFailure(
                    "saveOrReplace",
                    ErrorCode.GOOGLE_CALENDAR_INTEGRATION_SAVE_FAILED,
                    exception
            );
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_INTEGRATION_SAVE_FAILED, exception);
        }
    }

    private void logIntegrationFailure(String operation, ErrorCode errorCode) {
        log.warn(
                "Google Calendar integration failure. operation={} errorCode={}",
                operation,
                errorCode.name()
        );
    }

    private void logIntegrationFailure(String operation, ErrorCode errorCode, Exception exception) {
        log.warn(
                "Google Calendar integration failure. operation={} errorCode={} causeType={}",
                operation,
                errorCode.name(),
                exception.getClass().getSimpleName()
        );
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
