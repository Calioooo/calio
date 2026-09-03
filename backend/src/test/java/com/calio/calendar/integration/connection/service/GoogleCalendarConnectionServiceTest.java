package com.calio.calendar.integration.connection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.service.AccountCommandService;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleOAuthClient;
import com.calio.calendar.external.google.GoogleOAuthProperties;
import com.calio.calendar.external.google.dto.GoogleTokenResponse;
import com.calio.calendar.external.google.dto.GoogleUserInfoResponse;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnectionState;
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
    private final AccountCommandService accountCommandService = mock(AccountCommandService.class);
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
        GoogleCalendarIntegration integration = integration();
        GoogleCalendarConnection connection = connection(integration);
        when(oauthClient.exchangeAuthorizationCode("authorization-code"))
                .thenReturn(new GoogleTokenResponse("access-token", "refresh-token", 3600));
        when(oauthClient.fetchUserInfo("access-token"))
                .thenReturn(new GoogleUserInfoResponse("google-subject", "google@example.com"));
        when(integrationCommandService.tryLockIntegration(ACCOUNT_ID)).thenReturn(Optional.empty());
        when(integrationCommandService.createIntegration(ACCOUNT_ID)).thenReturn(integration);
        when(connectionCommandService.tryLockConnectionByIntegrationAndState(
                integration.getId(),
                GoogleCalendarConnectionState.CONNECTED
        ))
                .thenReturn(Optional.empty());
        when(connectionCommandService.tryLockConnection(integration.getId(), "google-subject"))
                .thenReturn(Optional.empty());
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
    @DisplayName("Account 잠금 뒤 이미 생성된 Integration을 조회하면 새 Integration을 만들지 않는다")
    void givenIntegrationCreatedWhileWaitingForAccountLock_whenConnect_thenUsesExistingIntegration() {
        // given
        GoogleCalendarIntegration integration = new GoogleCalendarIntegration(ACCOUNT_ID);
        GoogleCalendarConnection connection = connection(integration);
        when(oauthClient.exchangeAuthorizationCode("authorization-code"))
                .thenReturn(new GoogleTokenResponse("access-token", "refresh-token", 3600));
        when(oauthClient.fetchUserInfo("access-token"))
                .thenReturn(new GoogleUserInfoResponse("google-subject", "google@example.com"));
        when(integrationCommandService.tryLockIntegration(ACCOUNT_ID))
                .thenReturn(Optional.of(integration));
        when(connectionCommandService.tryLockConnectionByIntegrationAndState(
                integration.getId(),
                GoogleCalendarConnectionState.CONNECTED
        )).thenReturn(Optional.empty());
        when(connectionCommandService.tryLockConnection(integration.getId(), "google-subject"))
                .thenReturn(Optional.empty());
        when(connectionCommandService.createConnection(
                eq(integration),
                eq("google-subject"),
                eq("google@example.com"),
                anyString(),
                anyString(),
                eq(NOW.plusSeconds(3600)),
                eq(NOW)
        )).thenReturn(connection);

        // when
        var response = service().connect(ACCOUNT_ID, "authorization-code");

        // then
        assertThat(response.connected()).isTrue();
        var callOrder = inOrder(accountCommandService, connectionCommandService, integrationCommandService);
        callOrder.verify(accountCommandService).lockAccount(ACCOUNT_ID);
        callOrder.verify(integrationCommandService).tryLockIntegration(ACCOUNT_ID);
        verify(connectionCommandService).createConnection(
                eq(integration),
                eq("google-subject"),
                eq("google@example.com"),
                anyString(),
                anyString(),
                eq(NOW.plusSeconds(3600)),
                eq(NOW)
        );
        verify(integrationCommandService, never()).createIntegration(ACCOUNT_ID);
        verify(enqueueService).enqueueManualSync(ACCOUNT_ID);
    }

    @Test
    @DisplayName("연결된 같은 Google subject를 다시 연결하려면 먼저 해제해야 한다")
    void givenConnectedSameSubject_whenConnect_thenRequiresDisconnectFirst() {
        GoogleCalendarIntegration integration = integration();
        GoogleCalendarConnection connection = connection(integration);
        when(oauthClient.exchangeAuthorizationCode("authorization-code"))
                .thenReturn(new GoogleTokenResponse("new-access-token", "new-refresh-token", 3600));
        when(oauthClient.fetchUserInfo("new-access-token"))
                .thenReturn(new GoogleUserInfoResponse("google-subject", "new@example.com"));
        when(integrationCommandService.tryLockIntegration(ACCOUNT_ID)).thenReturn(Optional.of(integration));
        when(connectionCommandService.tryLockConnectionByIntegrationAndState(
                integration.getId(),
                GoogleCalendarConnectionState.CONNECTED
        ))
                .thenReturn(Optional.of(connection));

        assertThatThrownBy(() -> service().connect(ACCOUNT_ID, "authorization-code"))
                .isInstanceOf(CalioException.class)
                .extracting(exception -> ((CalioException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED);

        verify(connectionCommandService, never()).replaceCredentials(
                any(), anyString(), anyString(), anyString(), any(), any());
        verify(connectionCommandService, never()).createConnection(
                any(), anyString(), anyString(), anyString(), anyString(), any(), any());
        verifyNoInteractions(enqueueService);
    }

    @Test
    @DisplayName("연결 해제된 같은 Google subject는 retained Connection을 다시 활성화한다")
    void givenDisconnectedSameSubject_whenConnect_thenReactivatesRetainedConnection() {
        GoogleCalendarIntegration integration = integration();
        GoogleCalendarConnection connection = connection(integration);
        connection.disconnect(NOW.minusSeconds(60));
        when(oauthClient.exchangeAuthorizationCode("authorization-code"))
                .thenReturn(new GoogleTokenResponse("new-access-token", "new-refresh-token", 3600));
        when(oauthClient.fetchUserInfo("new-access-token"))
                .thenReturn(new GoogleUserInfoResponse("google-subject", "new@example.com"));
        when(integrationCommandService.tryLockIntegration(ACCOUNT_ID)).thenReturn(Optional.of(integration));
        when(connectionCommandService.tryLockConnectionByIntegrationAndState(
                integration.getId(),
                GoogleCalendarConnectionState.CONNECTED
        ))
                .thenReturn(Optional.empty());
        when(connectionCommandService.tryLockConnection(integration.getId(), "google-subject"))
                .thenReturn(Optional.of(connection));

        service().connect(ACCOUNT_ID, "authorization-code");

        verify(connectionCommandService).replaceCredentials(
                eq(connection), eq("new@example.com"), anyString(), anyString(),
                eq(NOW.plusSeconds(3600)), eq(NOW)
        );
        verify(connectionCommandService, never()).createConnection(
                any(), anyString(), anyString(), anyString(), anyString(), any(), any());
        verify(enqueueService).enqueueManualSync(ACCOUNT_ID);
    }

    @Test
    @DisplayName("다른 Google subject가 연결된 상태에서는 기존 Connection을 교체하지 않는다")
    void givenConnectedDifferentSubject_whenConnect_thenRejectsConnectionReplacement() {
        GoogleCalendarIntegration integration = integration();
        GoogleCalendarConnection activeConnection = connection(integration);
        when(oauthClient.exchangeAuthorizationCode("authorization-code"))
                .thenReturn(new GoogleTokenResponse("new-access-token", "new-refresh-token", 3600));
        when(oauthClient.fetchUserInfo("new-access-token"))
                .thenReturn(new GoogleUserInfoResponse("another-subject", "another@example.com"));
        when(integrationCommandService.tryLockIntegration(ACCOUNT_ID)).thenReturn(Optional.of(integration));
        when(connectionCommandService.tryLockConnectionByIntegrationAndState(
                integration.getId(),
                GoogleCalendarConnectionState.CONNECTED
        ))
                .thenReturn(Optional.of(activeConnection));

        assertThatThrownBy(() -> service().connect(ACCOUNT_ID, "authorization-code"))
                .isInstanceOf(CalioException.class)
                .extracting(exception -> ((CalioException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED);

        assertThat(activeConnection.getGoogleSubject()).isEqualTo("google-subject");
        verify(connectionCommandService, never()).createConnection(
                any(), anyString(), anyString(), anyString(), anyString(), any(), any());
        verifyNoInteractions(enqueueService);
    }

    @Test
    @DisplayName("연결 해제 후 다른 Google subject는 새 retained Connection으로 활성화한다")
    void givenDisconnectedSubject_whenConnectWithDifferentSubject_thenCreatesAnotherConnection() {
        GoogleCalendarIntegration integration = integration();
        GoogleCalendarConnection retainedConnection = connection(integration);
        retainedConnection.disconnect(NOW.minusSeconds(60));
        GoogleCalendarConnection newConnection = new GoogleCalendarConnection(
                integration,
                "another-subject",
                "another@example.com",
                encryptor.encryptRefreshToken("new-refresh-token"),
                encryptor.encryptAccessToken("new-access-token"),
                NOW.plusSeconds(3600),
                NOW
        );
        when(oauthClient.exchangeAuthorizationCode("authorization-code"))
                .thenReturn(new GoogleTokenResponse("new-access-token", "new-refresh-token", 3600));
        when(oauthClient.fetchUserInfo("new-access-token"))
                .thenReturn(new GoogleUserInfoResponse("another-subject", "another@example.com"));
        when(integrationCommandService.tryLockIntegration(ACCOUNT_ID)).thenReturn(Optional.of(integration));
        when(connectionCommandService.tryLockConnectionByIntegrationAndState(
                integration.getId(),
                GoogleCalendarConnectionState.CONNECTED
        ))
                .thenReturn(Optional.empty());
        when(connectionCommandService.tryLockConnection(integration.getId(), "another-subject"))
                .thenReturn(Optional.empty());
        when(connectionCommandService.createConnection(eq(integration), eq("another-subject"),
                eq("another@example.com"), anyString(), anyString(), eq(NOW.plusSeconds(3600)), eq(NOW)))
                .thenReturn(newConnection);

        service().connect(ACCOUNT_ID, "authorization-code");

        assertThat(retainedConnection.isConnected()).isFalse();
        verify(connectionCommandService).createConnection(eq(integration), eq("another-subject"),
                eq("another@example.com"), anyString(), anyString(), eq(NOW.plusSeconds(3600)), eq(NOW));
        verify(enqueueService).enqueueManualSync(ACCOUNT_ID);
    }

    @Test
    @DisplayName("연결 해제는 Connection의 Job을 제거하고 credential을 지운 뒤 revoke한다")
    void givenConnectedConnection_whenDisconnect_thenCleansConnectionBeforeRevokingToken() {
        GoogleCalendarIntegration integration = integration();
        GoogleCalendarConnection connection = connection(integration);
        when(integrationCommandService.tryLockIntegration(ACCOUNT_ID)).thenReturn(Optional.of(integration));
        when(connectionCommandService.tryLockDisconnectableConnectionByIntegration(integration.getId()))
                .thenReturn(Optional.of(connection));

        service().disconnect(ACCOUNT_ID);

        verify(jobCommandService).deleteJobsForIntegration(connection.getIntegration().getId());
        verify(connectionCommandService).disconnect(connection, NOW);
        verify(oauthClient).revokeToken("refresh-token");
    }

    @Test
    @DisplayName("sync error Connection도 해제하면 Job과 credential을 정리한다")
    void givenSyncErrorConnection_whenDisconnect_thenCleansConnectionBeforeRevokingToken() {
        GoogleCalendarIntegration integration = integration();
        GoogleCalendarConnection connection = connection(integration);
        connection.markSyncError("GOOGLE_CALENDAR_RECONNECT_REQUIRED", NOW.minusSeconds(60));
        when(integrationCommandService.tryLockIntegration(ACCOUNT_ID)).thenReturn(Optional.of(integration));
        when(connectionCommandService.tryLockDisconnectableConnectionByIntegration(integration.getId()))
                .thenReturn(Optional.of(connection));

        service().disconnect(ACCOUNT_ID);

        verify(jobCommandService).deleteJobsForIntegration(integration.getId());
        verify(connectionCommandService).disconnect(connection, NOW);
        verify(oauthClient).revokeToken("refresh-token");
    }

    @Test
    @DisplayName("연결되지 않은 Account를 해제해도 Job 삭제나 revoke를 수행하지 않는다")
    void givenNoConnectedConnection_whenDisconnect_thenDoesNothing() {
        when(integrationCommandService.tryLockIntegration(ACCOUNT_ID)).thenReturn(Optional.empty());

        service().disconnect(ACCOUNT_ID);

        verifyNoInteractions(jobCommandService, oauthClient);
    }

    private GoogleCalendarConnectionService service() {
        return new GoogleCalendarConnectionService(properties, oauthClient, encryptor, accountCommandService,
                integrationCommandService,
                connectionQueryService, connectionCommandService, jobCommandService, enqueueService,
                new NoOpTransactionManager(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private GoogleCalendarConnection connection(GoogleCalendarIntegration integration) {
        return new GoogleCalendarConnection(integration, "google-subject", "google@example.com",
                encryptor.encryptRefreshToken("refresh-token"), encryptor.encryptAccessToken("access-token"),
                NOW.plusSeconds(3600), NOW);
    }

    private GoogleCalendarIntegration integration() {
        GoogleCalendarIntegration integration = mock(GoogleCalendarIntegration.class);
        when(integration.getId()).thenReturn(10L);
        when(integration.getAccountId()).thenReturn(ACCOUNT_ID);
        return integration;
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
