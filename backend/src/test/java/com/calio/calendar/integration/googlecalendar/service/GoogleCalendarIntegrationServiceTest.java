package com.calio.calendar.integration.googlecalendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-google-service-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "external.google.oauth.client-id=client-id",
        "external.google.oauth.client-secret=client-secret",
        "external.google.oauth.redirect-uri=https://app.test/oauth/callback",
        "security.token-encryption.google-refresh-token-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class GoogleCalendarIntegrationServiceTest {

    @Autowired
    private GoogleCalendarIntegrationService service;

    @Autowired
    private GoogleCalendarIntegrationRepository integrationRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private FakeGoogleOAuthClient googleOAuthClient;

    @Autowired
    private RecordingSyncTokenCleanupService syncTokenCleanupService;

    @Autowired
    private GoogleOAuthProperties googleOAuthProperties;

    @Autowired
    private TokenEncryptionProperties tokenEncryptionProperties;

    private Account account;

    @BeforeEach
    void setUp() {
        integrationRepository.deleteAll();
        accountRepository.deleteAll();
        googleOAuthClient.reset();
        syncTokenCleanupService.reset();
        googleOAuthProperties.setClientId("client-id");
        googleOAuthProperties.setClientSecret("client-secret");
        googleOAuthProperties.setRedirectUri("https://app.test/oauth/callback");
        tokenEncryptionProperties.setGoogleRefreshTokenKey(
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
        );
        account = accountRepository.saveAndFlush(new Account());
    }

    @Test
    @DisplayName("connect는 Google token과 userinfo를 조회하고 refresh token envelope만 저장한다")
    void givenAuthorizationCode_whenConnect_thenStoresConnectedIntegrationWithoutPlainRefreshToken() {
        // when
        GoogleCalendarIntegrationResponse response = service.connect(account.getId(), "authorization-code");

        // then
        assertThat(response.connected()).isTrue();
        assertThat(response.googleEmail()).isEqualTo("user@example.com");
        assertThat(response.googleSubject()).isEqualTo("google-subject");
        assertThat(response.connectedAt()).isEqualTo(Instant.parse("2026-07-14T00:00:00Z"));

        GoogleCalendarIntegration integration = integrationRepository.findByAccountId(account.getId()).orElseThrow();
        assertThat(integration.getEncryptedRefreshToken()).startsWith("v1:1:");
        assertThat(integration.getEncryptedRefreshToken()).doesNotContain("refresh-token");
        assertThat(integration.getAccessToken()).isEqualTo("access-token");
        assertThat(integration.getAccessTokenExpiresAt()).isEqualTo(Instant.parse("2026-07-14T01:00:00Z"));
        assertThat(googleOAuthClient.authorizationCodes()).containsExactly("authorization-code");
    }

    @Test
    @DisplayName("이미 연결된 계정의 connect는 Google 외부 호출 없이 conflict를 반환한다")
    void givenConnectedIntegration_whenConnectAgain_thenThrowsAlreadyConnectedBeforeGoogleCall() {
        // given
        service.connect(account.getId(), "authorization-code");
        googleOAuthClient.reset();

        // when then
        assertThatThrownBy(() -> service.connect(account.getId(), "new-authorization-code"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_INTEGRATION_ALREADY_CONNECTED));
        assertThat(googleOAuthClient.authorizationCodes()).isEmpty();
    }

    @Test
    @DisplayName("저장된 row가 없으면 status는 disconnected shape를 반환한다")
    void givenNoIntegration_whenGetStatus_thenReturnsDisconnectedShape() {
        // when
        GoogleCalendarIntegrationResponse response = service.getStatus(account.getId());

        // then
        assertThat(response.connected()).isFalse();
        assertThat(response.googleEmail()).isNull();
        assertThat(response.googleSubject()).isNull();
        assertThat(response.connectedAt()).isNull();
    }

    @Test
    @DisplayName("token 교환 실패 시 Integration row를 생성하지 않는다")
    void givenTokenExchangeFailure_whenConnect_thenDoesNotPersistIntegration() {
        // given
        googleOAuthClient.failTokenExchange();

        // when then
        assertThatThrownBy(() -> service.connect(account.getId(), "authorization-code"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_OAUTH_TOKEN_EXCHANGE_FAILED));
        assertThat(integrationRepository.existsByAccountId(account.getId())).isFalse();
    }

    @Test
    @DisplayName("필수 설정이 없으면 Google 외부 호출 전에 GOOGLE_OAUTH_CONFIGURATION_MISSING으로 실패한다")
    void givenMissingConfiguration_whenConnect_thenFailsBeforeGoogleCall() {
        // given
        googleOAuthProperties.setClientSecret("");

        // when then
        assertThatThrownBy(() -> service.connect(account.getId(), "authorization-code"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_OAUTH_CONFIGURATION_MISSING));
        assertThat(googleOAuthClient.authorizationCodes()).isEmpty();
        assertThat(integrationRepository.existsByAccountId(account.getId())).isFalse();
    }

    @Test
    @DisplayName("disconnect는 sync-token cleanup을 호출하고 Integration row 삭제를 idempotent하게 처리한다")
    void givenConnectedIntegration_whenDisconnectTwice_thenDeletesIntegrationAndInvokesCleanup() {
        // given
        service.connect(account.getId(), "authorization-code");

        // when
        service.disconnect(account.getId());
        service.disconnect(account.getId());

        // then
        assertThat(integrationRepository.existsByAccountId(account.getId())).isFalse();
        assertThat(syncTokenCleanupService.accountIds()).containsExactly(account.getId(), account.getId());
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        @Primary
        FakeGoogleOAuthClient fakeGoogleOAuthClient() {
            return new FakeGoogleOAuthClient();
        }

        @Bean
        @Primary
        RecordingSyncTokenCleanupService recordingSyncTokenCleanupService() {
            return new RecordingSyncTokenCleanupService();
        }
    }

    static class FakeGoogleOAuthClient extends GoogleOAuthClient {

        private final List<String> authorizationCodes = new ArrayList<>();
        private boolean failTokenExchange;

        FakeGoogleOAuthClient() {
            super(new GoogleOAuthProperties(), new ObjectMapper());
        }

        @Override
        public GoogleTokenResponse exchangeAuthorizationCode(String authorizationCode) {
            authorizationCodes.add(authorizationCode);
            if (failTokenExchange) {
                throw new CalioException(ErrorCode.GOOGLE_OAUTH_TOKEN_EXCHANGE_FAILED);
            }
            return new GoogleTokenResponse("access-token", "refresh-token", 3600);
        }

        @Override
        public GoogleUserInfoResponse fetchUserInfo(String accessToken) {
            return new GoogleUserInfoResponse("google-subject", "user@example.com");
        }

        void failTokenExchange() {
            this.failTokenExchange = true;
        }

        List<String> authorizationCodes() {
            return authorizationCodes;
        }

        void reset() {
            authorizationCodes.clear();
            failTokenExchange = false;
        }
    }

    static class RecordingSyncTokenCleanupService extends GoogleCalendarSyncTokenCleanupService {

        private final List<Long> accountIds = new ArrayList<>();

        @Override
        public void deleteAllByAccountId(Long accountId) {
            accountIds.add(accountId);
        }

        List<Long> accountIds() {
            return accountIds;
        }

        void reset() {
            accountIds.clear();
        }
    }
}
