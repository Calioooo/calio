package com.calio.calendar.integration.connection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.calio.calendar.external.google.GoogleOAuthClient;
import com.calio.calendar.external.google.GoogleOAuthProperties;
import com.calio.calendar.external.google.dto.GoogleTokenResponse;
import com.calio.calendar.external.google.dto.GoogleUserInfoResponse;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobCommandService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class GoogleCalendarConnectionServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");

    private final GoogleOAuthProperties properties = configuredProperties();
    private final GoogleOAuthClient oauthClient = mock(GoogleOAuthClient.class);
    private final TokenEncryptor encryptor = new TokenEncryptor(new TokenEncryptionConfig()
            .googleTokenBytesEncryptor(encryptionProperties()));
    private final GoogleCalendarIntegrationCommandService integrationCommandService =
            mock(GoogleCalendarIntegrationCommandService.class);
    private final GoogleCalendarConnectionQueryService connectionQueryService =
            mock(GoogleCalendarConnectionQueryService.class);
    private final GoogleCalendarConnectionCommandService connectionCommandService =
            mock(GoogleCalendarConnectionCommandService.class);
    private final GoogleOperationJobCommandService jobCommandService = mock(GoogleOperationJobCommandService.class);
    private final GoogleOperationJobEnqueueService enqueueService = mock(GoogleOperationJobEnqueueService.class);

    @Test
    @DisplayName("첫 연결은 Account Integration을 만들고 Google Connection에 credential을 저장한다")
    void givenNoConnection_whenConnect_thenCreatesIntegrationAndConnection() {
        GoogleCalendarIntegration integration = new GoogleCalendarIntegration(ACCOUNT_ID);
        GoogleCalendarConnection connection = connection(integration);
        when(oauthClient.exchangeAuthorizationCode("authorization-code"))
                .thenReturn(new GoogleTokenResponse("access-token", "refresh-token", 3600));
        when(oauthClient.fetchUserInfo("access-token"))
                .thenReturn(new GoogleUserInfoResponse("google-subject", "google@example.com"));
        when(connectionCommandService.tryLockConnection(ACCOUNT_ID)).thenReturn(Optional.empty());
        when(integrationCommandService.tryLockIntegration(ACCOUNT_ID)).thenReturn(Optional.empty());
        when(integrationCommandService.createIntegration(ACCOUNT_ID)).thenReturn(integration);
        when(connectionCommandService.createConnection(eq(integration), eq("google-subject"), eq("google@example.com"),
                anyString(), anyString(), eq(NOW.plusSeconds(3600)), eq(NOW))).thenReturn(connection);

        var response = service().connect(ACCOUNT_ID, "authorization-code");

        ArgumentCaptor<String> refreshToken = ArgumentCaptor.forClass(String.class);
        verify(connectionCommandService).createConnection(eq(integration), eq("google-subject"), eq("google@example.com"),
                refreshToken.capture(), anyString(), eq(NOW.plusSeconds(3600)), eq(NOW));
        assertThat(refreshToken.getValue()).isNotEqualTo("refresh-token");
        assertThat(response.connected()).isTrue();
        verify(enqueueService).enqueueManualSync(ACCOUNT_ID);
    }

    @Test
    @DisplayName("같은 Google subject 재연결은 기존 Connection의 credential만 교체한다")
    void givenSameSubjectConnection_whenConnect_thenReplacesCredentials() {
        GoogleCalendarConnection connection = connection(new GoogleCalendarIntegration(ACCOUNT_ID));
        when(oauthClient.exchangeAuthorizationCode("authorization-code"))
                .thenReturn(new GoogleTokenResponse("new-access-token", "new-refresh-token", 3600));
        when(oauthClient.fetchUserInfo("new-access-token"))
                .thenReturn(new GoogleUserInfoResponse("google-subject", "new@example.com"));
        when(connectionCommandService.tryLockConnection(ACCOUNT_ID)).thenReturn(Optional.of(connection));

        service().connect(ACCOUNT_ID, "authorization-code");

        verify(connectionCommandService).replaceCredentials(
                eq(connection),
                eq("new@example.com"),
                anyString(),
                anyString(),
                eq(NOW.plusSeconds(3600)),
                eq(NOW)
        );
        verify(integrationCommandService, org.mockito.Mockito.never()).createIntegration(any());
        verify(enqueueService).enqueueManualSync(ACCOUNT_ID);
    }

    @Test
    @DisplayName("연결 해제는 Connection의 Job을 제거하고 credential을 지운 뒤 revoke한다")
    void givenConnectedConnection_whenDisconnect_thenCleansConnectionBeforeRevokingToken() {
        GoogleCalendarConnection connection = connection(new GoogleCalendarIntegration(ACCOUNT_ID));
        when(connectionCommandService.tryLockConnectedConnection(ACCOUNT_ID)).thenReturn(Optional.of(connection));

        service().disconnect(ACCOUNT_ID);

        verify(jobCommandService).deleteJobsForConnection(connection.getId());
        verify(connectionCommandService).disconnect(connection, NOW);
        verify(oauthClient).revokeToken("refresh-token");
    }

    @Test
    @DisplayName("연결되지 않은 Account를 해제해도 Job 삭제나 revoke를 수행하지 않는다")
    void givenNoConnectedConnection_whenDisconnect_thenDoesNothing() {
        when(connectionCommandService.tryLockConnectedConnection(ACCOUNT_ID)).thenReturn(Optional.empty());

        service().disconnect(ACCOUNT_ID);

        verifyNoInteractions(jobCommandService, oauthClient);
    }

    private GoogleCalendarConnectionService service() {
        return new GoogleCalendarConnectionService(properties, oauthClient, encryptor, integrationCommandService,
                connectionQueryService, connectionCommandService, jobCommandService, enqueueService,
                new NoOpTransactionManager(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private GoogleCalendarConnection connection(GoogleCalendarIntegration integration) {
        return new GoogleCalendarConnection(integration, "google-subject", "google@example.com",
                encryptor.encryptRefreshToken("refresh-token"), encryptor.encryptAccessToken("access-token"),
                NOW.plusSeconds(3600), NOW);
    }

    private GoogleOAuthProperties configuredProperties() {
        GoogleOAuthProperties configured = new GoogleOAuthProperties();
        configured.setClientId("client-id");
        configured.setClientSecret("client-secret");
        configured.setRedirectUri("https://example.com/callback");
        return configured;
    }

    private TokenEncryptionProperties encryptionProperties() {
        TokenEncryptionProperties configured = new TokenEncryptionProperties();
        configured.setGoogleRefreshTokenKey("12345678901234567890123456789012");
        return configured;
    }

    private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
}
