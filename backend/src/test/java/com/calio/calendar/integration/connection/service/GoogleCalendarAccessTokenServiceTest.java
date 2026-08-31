package com.calio.calendar.integration.connection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.calio.calendar.external.google.GoogleOAuthClient;
import com.calio.calendar.external.google.GoogleCalendarInvalidGrantException;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobCommandService;
import com.calio.calendar.security.TokenEncryptionConfig;
import com.calio.calendar.security.TokenEncryptionProperties;
import com.calio.calendar.security.TokenEncryptor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class GoogleCalendarAccessTokenServiceTest {

    @Test
    @DisplayName("유효한 access token은 Connection에서 읽고 refresh 요청 없이 복호화한다")
    void givenUsableConnectionToken_whenGetAccessToken_thenReturnsDecryptedTokenWithoutRefresh() {
        TokenEncryptor encryptor = encryptor();
        GoogleCalendarConnection connection = new GoogleCalendarConnection(
                new GoogleCalendarIntegration(1L), "subject", "google@example.com",
                encryptor.encryptRefreshToken("refresh-token"), encryptor.encryptAccessToken("access-token"),
                Instant.parse("2026-08-28T02:00:00Z"), Instant.parse("2026-08-28T00:00:00Z"));
        GoogleCalendarConnectionQueryService connectionQueryService = mock(GoogleCalendarConnectionQueryService.class);
        GoogleCalendarConnectionCommandService connectionCommandService = mock(GoogleCalendarConnectionCommandService.class);
        GoogleOAuthClient oauthClient = mock(GoogleOAuthClient.class);
        GoogleOperationJobCommandService jobCommandService = mock(GoogleOperationJobCommandService.class);
        when(connectionQueryService.getConnectedConnectionById(10L)).thenReturn(connection);
        GoogleCalendarAccessTokenService service = new GoogleCalendarAccessTokenService(
                connectionQueryService, connectionCommandService, oauthClient, encryptor, jobCommandService,
                new NoOpTransactionManager(), Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC));

        String accessToken = service.getAccessToken(10L);

        assertThat(accessToken).isEqualTo("access-token");
        verify(connectionQueryService).getConnectedConnectionById(10L);
        verifyNoInteractions(oauthClient, connectionCommandService, jobCommandService);
    }

    @Test
    @DisplayName("invalid_grant 처리 중 Connection이 이미 해제됐으면 재연결 필요 오류를 유지한다")
    void givenDisconnectedConnectionDuringInvalidGrant_whenForceRefresh_thenKeepsReconnectRequiredError() {
        TokenEncryptor encryptor = encryptor();
        GoogleCalendarConnection connection = mock(GoogleCalendarConnection.class);
        GoogleCalendarConnectionQueryService connectionQueryService = mock(GoogleCalendarConnectionQueryService.class);
        GoogleCalendarConnectionCommandService connectionCommandService = mock(GoogleCalendarConnectionCommandService.class);
        GoogleOAuthClient oauthClient = mock(GoogleOAuthClient.class);
        GoogleOperationJobCommandService jobCommandService = mock(GoogleOperationJobCommandService.class);
        when(connection.getId()).thenReturn(10L);
        when(connection.getEncryptedRefreshToken()).thenReturn(encryptor.encryptRefreshToken("refresh-token"));
        when(connection.getEncryptedAccessToken()).thenReturn(encryptor.encryptAccessToken("expired-access-token"));
        when(connection.getAccessTokenExpiresAt()).thenReturn(Instant.parse("2026-08-27T00:00:00Z"));
        when(connectionQueryService.getConnectedConnectionById(10L)).thenReturn(connection);
        when(oauthClient.refreshAccessToken("refresh-token"))
                .thenThrow(new GoogleCalendarInvalidGrantException(new RuntimeException()));
        when(connectionCommandService.tryLockConnectedConnectionById(10L)).thenReturn(Optional.empty());
        GoogleCalendarAccessTokenService service = new GoogleCalendarAccessTokenService(
                connectionQueryService, connectionCommandService, oauthClient, encryptor, jobCommandService,
                new NoOpTransactionManager(), Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.forceRefresh(10L))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED));
        verifyNoInteractions(jobCommandService);
    }

    private TokenEncryptor encryptor() {
        TokenEncryptionProperties properties = new TokenEncryptionProperties();
        properties.setGoogleRefreshTokenKey("12345678901234567890123456789012");
        return new TokenEncryptor(new TokenEncryptionConfig().googleTokenBytesEncryptor(properties));
    }

    private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
}
