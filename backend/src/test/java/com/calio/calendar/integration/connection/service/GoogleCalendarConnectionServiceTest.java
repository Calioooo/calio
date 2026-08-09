package com.calio.calendar.integration.connection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleOAuthClient;
import com.calio.calendar.external.google.GoogleOAuthProperties;
import com.calio.calendar.external.google.dto.GoogleTokenResponse;
import com.calio.calendar.external.google.dto.GoogleUserInfoResponse;
import com.calio.calendar.integration.connection.controller.dto.GoogleCalendarIntegrationResponse;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.security.TokenEncryptionConfig;
import com.calio.calendar.security.TokenEncryptionProperties;
import com.calio.calendar.security.TokenEncryptor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class GoogleCalendarConnectionServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-07-14T12:00:00Z");

    private final GoogleOAuthProperties googleOAuthProperties = googleOAuthProperties();
    private final BytesEncryptor bytesEncryptor = new TokenEncryptionConfig()
            .googleTokenBytesEncryptor(tokenEncryptionProperties());
    private final TokenEncryptor tokenEncryptor = new TokenEncryptor(bytesEncryptor);
    private final GoogleCalendarIntegrationQueryService integrationQueryService =
            mock(GoogleCalendarIntegrationQueryService.class);
    private final GoogleCalendarIntegrationCommandService integrationCommandService =
            mock(GoogleCalendarIntegrationCommandService.class);
    private final GoogleCalendarIntegrationDataService integrationDataService =
            mock(GoogleCalendarIntegrationDataService.class);
    private final GoogleOperationJobCommandService jobCommandService =
            mock(GoogleOperationJobCommandService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("connect는 Google token을 암호화한 뒤 새 연결을 저장한다")
    void givenValidAuthorizationCode_whenConnect_thenCreatesEncryptedConnection() {
        // given
        FakeGoogleOAuthClient googleOAuthClient = connectedGoogleOAuthClient();
        when(integrationCommandService.tryLockIntegration(ACCOUNT_ID))
                .thenReturn(Optional.empty());
        when(integrationCommandService.createIntegration(
                eq(ACCOUNT_ID),
                eq("google-subject"),
                eq("user@example.com"),
                anyString(),
                anyString(),
                eq(NOW.plusSeconds(3600)),
                eq(NOW)
        )).thenReturn(new GoogleCalendarIntegration(
                ACCOUNT_ID,
                "google-subject",
                "user@example.com",
                "stored-refresh-token",
                "stored-access-token",
                NOW.plusSeconds(3600),
                NOW
        ));
        GoogleCalendarConnectionService service = service(googleOAuthClient);

        // when
        GoogleCalendarIntegrationResponse response = service.connect(
                ACCOUNT_ID,
                "auth-code"
        );

        // then
        ArgumentCaptor<String> refreshTokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> accessTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(integrationCommandService).createIntegration(
                eq(ACCOUNT_ID),
                eq("google-subject"),
                eq("user@example.com"),
                refreshTokenCaptor.capture(),
                accessTokenCaptor.capture(),
                eq(NOW.plusSeconds(3600)),
                eq(NOW)
        );
        assertThat(response.connected()).isTrue();
        assertThat(refreshTokenCaptor.getValue()).isNotEqualTo("refresh-token");
        assertThat(accessTokenCaptor.getValue()).isNotEqualTo("access-token");
    }

    @Test
    @DisplayName("connect에서 Google token 교환이 실패하면 로컬 연결을 변경하지 않는다")
    void givenTokenExchangeFailure_whenConnect_thenDoesNotChangeConnection() {
        // given
        FakeGoogleOAuthClient googleOAuthClient = new FakeGoogleOAuthClient(googleOAuthProperties);
        googleOAuthClient.tokenExchangeException =
                new CalioException(ErrorCode.GOOGLE_TOKEN_EXCHANGE_FAILED);
        GoogleCalendarConnectionService service = service(googleOAuthClient);

        // when, then
        assertThatThrownBy(() -> service.connect(
                ACCOUNT_ID,
                "auth-code"
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GOOGLE_TOKEN_EXCHANGE_FAILED));
        verifyNoInteractions(integrationCommandService, integrationDataService, jobCommandService);
    }

    @Test
    @DisplayName("connect 등록 경쟁이 발생하면 rollback 후 새 트랜잭션으로 한 번 재시도한다")
    void givenConnectionRegistrationRace_whenConnect_thenRetriesOnce() {
        // given
        FakeGoogleOAuthClient googleOAuthClient = connectedGoogleOAuthClient();
        GoogleCalendarIntegration existingIntegration = integrationWithRefreshToken("old-token");
        when(integrationCommandService.tryLockIntegration(ACCOUNT_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingIntegration));
        when(integrationCommandService.createIntegration(
                eq(ACCOUNT_ID),
                eq("google-subject"),
                eq("user@example.com"),
                anyString(),
                anyString(),
                eq(NOW.plusSeconds(3600)),
                eq(NOW)
        )).thenThrow(new DataIntegrityViolationException("registration race"));
        when(integrationCommandService.replaceIntegration(
                eq(existingIntegration),
                eq("google-subject"),
                eq("user@example.com"),
                anyString(),
                anyString(),
                eq(NOW.plusSeconds(3600)),
                eq(NOW)
        )).thenReturn(existingIntegration);
        GoogleCalendarConnectionService service = service(googleOAuthClient);

        // when
        GoogleCalendarIntegrationResponse response = service.connect(
                ACCOUNT_ID,
                "auth-code"
        );

        // then
        assertThat(response.connected()).isTrue();
        verify(integrationCommandService, times(2)).tryLockIntegration(ACCOUNT_ID);
        verify(integrationDataService).deleteIntegrationData(existingIntegration.getId());
    }

    @Test
    @DisplayName("disconnect는 Google revoke 후 Job, integration data, 연결 순서로 제거한다")
    void givenConnectedIntegration_whenDisconnect_thenRemovesLocalConnectionInOrder() {
        // given
        FakeGoogleOAuthClient googleOAuthClient = new FakeGoogleOAuthClient(googleOAuthProperties);
        GoogleCalendarIntegration integration = integrationWithRefreshToken("refresh-token");
        when(integrationQueryService.getIntegrationIfExists(ACCOUNT_ID))
                .thenReturn(Optional.of(integration));
        when(integrationCommandService.tryLockIntegration(ACCOUNT_ID))
                .thenReturn(Optional.of(integration));
        GoogleCalendarConnectionService service = service(googleOAuthClient);

        // when
        service.disconnect(ACCOUNT_ID);

        // then
        assertThat(googleOAuthClient.revokedToken).isEqualTo("refresh-token");
        InOrder removalOrder = inOrder(
                jobCommandService,
                integrationDataService,
                integrationCommandService
        );
        removalOrder.verify(jobCommandService).deleteJobsForIntegration(integration.getId());
        removalOrder.verify(integrationDataService).deleteIntegrationData(integration.getId());
        removalOrder.verify(integrationCommandService).deleteIntegration(integration);
    }

    @Test
    @DisplayName("disconnect는 Google revoke 실패 시 로컬 연결을 유지한다")
    void givenUnexpectedRevokeFailure_whenDisconnect_thenKeepsConnection() {
        // given
        FakeGoogleOAuthClient googleOAuthClient = new FakeGoogleOAuthClient(googleOAuthProperties);
        googleOAuthClient.tokenRevokeException =
                new CalioException(ErrorCode.GOOGLE_TOKEN_REVOKE_FAILED);
        when(integrationQueryService.getIntegrationIfExists(ACCOUNT_ID))
                .thenReturn(Optional.of(integrationWithRefreshToken("refresh-token")));
        GoogleCalendarConnectionService service = service(googleOAuthClient);

        // when, then
        assertThatThrownBy(() -> service.disconnect(ACCOUNT_ID))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_TOKEN_REVOKE_FAILED));
        verify(integrationCommandService, never()).tryLockIntegration(ACCOUNT_ID);
        verifyNoInteractions(integrationDataService, jobCommandService);
    }

    private GoogleCalendarConnectionService service(
            FakeGoogleOAuthClient googleOAuthClient
    ) {
        return new GoogleCalendarConnectionService(
                googleOAuthProperties,
                googleOAuthClient,
                tokenEncryptor,
                integrationQueryService,
                integrationCommandService,
                integrationDataService,
                jobCommandService,
                new NoOpTransactionManager(),
                clock
        );
    }

    private FakeGoogleOAuthClient connectedGoogleOAuthClient() {
        FakeGoogleOAuthClient googleOAuthClient = new FakeGoogleOAuthClient(googleOAuthProperties);
        googleOAuthClient.tokenResponse =
                new GoogleTokenResponse("access-token", "refresh-token", 3600);
        googleOAuthClient.userInfoResponse =
                new GoogleUserInfoResponse("google-subject", "user@example.com");
        return googleOAuthClient;
    }

    private GoogleOAuthProperties googleOAuthProperties() {
        GoogleOAuthProperties properties = new GoogleOAuthProperties();
        properties.setTokenUrl("https://oauth2.googleapis.com/token");
        properties.setUserInfoUrl("https://www.googleapis.com/oauth2/v3/userinfo");
        properties.setRevokeUrl("https://oauth2.googleapis.com/revoke");
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

    private GoogleCalendarIntegration integrationWithRefreshToken(String refreshToken) {
        return new GoogleCalendarIntegration(
                ACCOUNT_ID,
                "google-subject",
                "user@example.com",
                tokenEncryptor.encryptRefreshToken(refreshToken),
                tokenEncryptor.encryptAccessToken("access-token"),
                NOW.plusSeconds(3600),
                NOW
        );
    }

    private static class FakeGoogleOAuthClient extends GoogleOAuthClient {

        private GoogleTokenResponse tokenResponse;
        private GoogleUserInfoResponse userInfoResponse;
        private RuntimeException tokenExchangeException;
        private RuntimeException tokenRevokeException;
        private String revokedToken;

        FakeGoogleOAuthClient(GoogleOAuthProperties properties) {
            super(properties, new ObjectMapper(), RestClient.builder().build());
        }

        @Override
        public GoogleTokenResponse exchangeAuthorizationCode(String authorizationCode) {
            if (tokenExchangeException != null) {
                throw tokenExchangeException;
            }
            return tokenResponse;
        }

        @Override
        public GoogleUserInfoResponse fetchUserInfo(String accessToken) {
            return userInfoResponse;
        }

        @Override
        public boolean revokeToken(String token) {
            if (tokenRevokeException != null) {
                throw tokenRevokeException;
            }
            revokedToken = token;
            return true;
        }
    }

    private static class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
