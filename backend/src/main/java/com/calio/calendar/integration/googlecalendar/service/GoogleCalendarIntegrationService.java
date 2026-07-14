package com.calio.calendar.integration.googlecalendar.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.googlecalendar.client.GoogleOAuthClient;
import com.calio.calendar.integration.googlecalendar.client.GoogleTokenResponse;
import com.calio.calendar.integration.googlecalendar.client.GoogleUserInfoResponse;
import com.calio.calendar.integration.googlecalendar.config.GoogleOAuthProperties;
import com.calio.calendar.integration.googlecalendar.config.TokenEncryptionProperties;
import com.calio.calendar.integration.googlecalendar.controller.dto.GoogleCalendarIntegrationResponse;
import com.calio.calendar.integration.googlecalendar.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.googlecalendar.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.security.GoogleRefreshTokenEncryptor;
import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleCalendarIntegrationService {

    private final GoogleOAuthProperties googleOAuthProperties;
    private final TokenEncryptionProperties tokenEncryptionProperties;
    private final GoogleOAuthClient googleOAuthClient;
    private final GoogleRefreshTokenEncryptor refreshTokenEncryptor;
    private final GoogleCalendarIntegrationRepository googleCalendarIntegrationRepository;
    private final GoogleCalendarIntegrationPersistenceService persistenceService;
    private final Clock clock;

    public GoogleCalendarIntegrationService(
            GoogleOAuthProperties googleOAuthProperties,
            TokenEncryptionProperties tokenEncryptionProperties,
            GoogleOAuthClient googleOAuthClient,
            GoogleRefreshTokenEncryptor refreshTokenEncryptor,
            GoogleCalendarIntegrationRepository googleCalendarIntegrationRepository,
            GoogleCalendarIntegrationPersistenceService persistenceService,
            Clock clock
    ) {
        this.googleOAuthProperties = googleOAuthProperties;
        this.tokenEncryptionProperties = tokenEncryptionProperties;
        this.googleOAuthClient = googleOAuthClient;
        this.refreshTokenEncryptor = refreshTokenEncryptor;
        this.googleCalendarIntegrationRepository = googleCalendarIntegrationRepository;
        this.persistenceService = persistenceService;
        this.clock = clock;
    }

    public GoogleCalendarIntegrationResponse connect(Long accountId, String authorizationCode) {
        validateAuthorizationCode(authorizationCode);
        validateConfiguration();
        rejectAlreadyConnected(accountId);

        GoogleTokenResponse token = googleOAuthClient.exchangeAuthorizationCode(authorizationCode);
        GoogleUserInfoResponse userInfo = googleOAuthClient.fetchUserInfo(token.accessToken());
        String encryptedRefreshToken = refreshTokenEncryptor.encrypt(token.refreshToken());
        Instant connectedAt = Instant.now(clock);

        GoogleCalendarIntegration integration = new GoogleCalendarIntegration(
                accountId,
                userInfo.subject(),
                userInfo.email(),
                encryptedRefreshToken,
                token.accessToken(),
                connectedAt.plusSeconds(token.expiresInSeconds()),
                connectedAt
        );
        return saveConnected(integration);
    }

    @Transactional(readOnly = true)
    public GoogleCalendarIntegrationResponse getStatus(Long accountId) {
        return googleCalendarIntegrationRepository.findByAccountId(accountId)
                .filter(GoogleCalendarIntegration::isConnected)
                .map(GoogleCalendarIntegrationResponse::connected)
                .orElseGet(GoogleCalendarIntegrationResponse::disconnected);
    }

    public void disconnect(Long accountId) {
        persistenceService.disconnect(accountId);
    }

    private void validateAuthorizationCode(String authorizationCode) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            throw new CalioException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private void validateConfiguration() {
        if (googleOAuthProperties.hasRequiredSettings()
                && tokenEncryptionProperties.hasValidGoogleRefreshTokenKey()) {
            return;
        }

        throw new CalioException(ErrorCode.GOOGLE_OAUTH_CONFIGURATION_MISSING);
    }

    private void rejectAlreadyConnected(Long accountId) {
        googleCalendarIntegrationRepository.findByAccountId(accountId)
                .filter(GoogleCalendarIntegration::isConnected)
                .ifPresent(integration -> {
                    throw new CalioException(ErrorCode.GOOGLE_CALENDAR_INTEGRATION_ALREADY_CONNECTED);
                });
    }

    private GoogleCalendarIntegrationResponse saveConnected(GoogleCalendarIntegration integration) {
        try {
            return persistenceService.saveConnected(integration);
        } catch (DataIntegrityViolationException exception) {
            if (googleCalendarIntegrationRepository.existsByAccountId(integration.getAccountId())) {
                throw new CalioException(ErrorCode.GOOGLE_CALENDAR_INTEGRATION_ALREADY_CONNECTED, exception);
            }
            throw exception;
        }
    }
}
