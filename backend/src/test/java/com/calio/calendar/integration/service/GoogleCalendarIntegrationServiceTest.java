package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleOAuthClient;
import com.calio.calendar.external.google.GoogleOAuthProperties;
import com.calio.calendar.external.google.dto.GoogleTokenResponse;
import com.calio.calendar.external.google.dto.GoogleUserInfoResponse;
import com.calio.calendar.integration.controller.dto.GoogleCalendarConnectRequest;
import com.calio.calendar.integration.controller.dto.GoogleCalendarIntegrationResponse;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.security.TokenEncryptionProperties;
import com.calio.calendar.security.TokenEncryptor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GoogleCalendarIntegrationServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-07-14T12:00:00Z");

    private final GoogleOAuthProperties googleOAuthProperties = googleOAuthProperties();
    private final TokenEncryptor tokenEncryptor = new TokenEncryptor(tokenEncryptionProperties());
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("connect는 Google token과 UserInfo를 받은 뒤 plaintext token이 아닌 암호화 envelope를 저장한다")
    void givenValidAuthorizationCode_whenConnect_thenStoresEncryptedTokensAndReturnsConnectedResponse() {
        // given
        FakeGoogleOAuthClient googleOAuthClient = new FakeGoogleOAuthClient(googleOAuthProperties);
        googleOAuthClient.tokenResponse = new GoogleTokenResponse("access-token", "refresh-token", 3600);
        googleOAuthClient.userInfoResponse = new GoogleUserInfoResponse("google-subject", "user@example.com");
        FakePersistenceService persistenceService = new FakePersistenceService();
        GoogleCalendarIntegrationService service = service(googleOAuthClient, persistenceService);

        // when
        GoogleCalendarIntegrationResponse response = service.connect(
                ACCOUNT_ID,
                new GoogleCalendarConnectRequest("auth-code")
        );

        // then
        assertThat(response.connected()).isTrue();
        assertThat(response.googleSubject()).isEqualTo("google-subject");
        assertThat(persistenceService.savedRefreshToken).isNotEqualTo("refresh-token");
        assertThat(persistenceService.savedAccessToken).isNotEqualTo("access-token");
        assertThat(persistenceService.savedAccessTokenExpiresAt)
                .isEqualTo(Instant.parse("2026-07-14T13:00:00Z"));
    }

    @Test
    @DisplayName("connect에서 Google token exchange가 실패하면 integration row 저장을 시도하지 않는다")
    void givenTokenExchangeFailure_whenConnect_thenDoesNotPersistIntegration() {
        // given
        FakeGoogleOAuthClient googleOAuthClient = new FakeGoogleOAuthClient(googleOAuthProperties);
        googleOAuthClient.tokenExchangeException = new CalioException(ErrorCode.GOOGLE_TOKEN_EXCHANGE_FAILED);
        FakePersistenceService persistenceService = new FakePersistenceService();
        GoogleCalendarIntegrationService service = service(googleOAuthClient, persistenceService);

        // when, then
        assertThatThrownBy(() -> service.connect(ACCOUNT_ID, new GoogleCalendarConnectRequest("auth-code")))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GOOGLE_TOKEN_EXCHANGE_FAILED));
        assertThat(persistenceService.saveCount).isZero();
    }

    private GoogleCalendarIntegrationService service(
            FakeGoogleOAuthClient googleOAuthClient,
            FakePersistenceService persistenceService
    ) {
        return new GoogleCalendarIntegrationService(
                googleOAuthProperties,
                googleOAuthClient,
                tokenEncryptor,
                persistenceService,
                clock
        );
    }

    private GoogleOAuthProperties googleOAuthProperties() {
        GoogleOAuthProperties properties = new GoogleOAuthProperties();
        properties.setTokenUrl("https://oauth2.googleapis.com/token");
        properties.setUserInfoUrl("https://www.googleapis.com/oauth2/v3/userinfo");
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setRedirectUri("https://example.com/oauth/callback");
        return properties;
    }

    private TokenEncryptionProperties tokenEncryptionProperties() {
        TokenEncryptionProperties properties = new TokenEncryptionProperties();
        properties.setGoogleRefreshTokenKey("12345678901234567890123456789012");
        return properties;
    }

    private static class FakeGoogleOAuthClient extends GoogleOAuthClient {

        private GoogleTokenResponse tokenResponse;
        private GoogleUserInfoResponse userInfoResponse;
        private RuntimeException tokenExchangeException;
        private int exchangeCount;

        FakeGoogleOAuthClient(GoogleOAuthProperties properties) {
            super(properties, new ObjectMapper());
        }

        @Override
        public GoogleTokenResponse exchangeAuthorizationCode(String authorizationCode) {
            exchangeCount++;
            if (tokenExchangeException != null) {
                throw tokenExchangeException;
            }
            return tokenResponse;
        }

        @Override
        public GoogleUserInfoResponse fetchUserInfo(String accessToken) {
            return userInfoResponse;
        }
    }

    private static class FakePersistenceService extends GoogleCalendarIntegrationPersistenceService {

        private int saveCount;
        private String savedRefreshToken;
        private String savedAccessToken;
        private Instant savedAccessTokenExpiresAt;

        FakePersistenceService() {
            super(null);
        }

        @Override
        public GoogleCalendarIntegration saveOrReplace(
                Long accountId,
                String googleSubject,
                String googleEmail,
                String encryptedRefreshToken,
                String encryptedAccessToken,
                Instant accessTokenExpiresAt,
                Instant connectedAt
        ) {
            saveCount++;
            savedRefreshToken = encryptedRefreshToken;
            savedAccessToken = encryptedAccessToken;
            savedAccessTokenExpiresAt = accessTokenExpiresAt;
            return new GoogleCalendarIntegration(
                    accountId,
                    googleSubject,
                    googleEmail,
                    encryptedRefreshToken,
                    encryptedAccessToken,
                    accessTokenExpiresAt,
                    connectedAt
            );
        }
    }
}
