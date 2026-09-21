package com.calio.calendar.integration.connection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnectionState;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.connection.repository.GoogleCalendarConnectionRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class GoogleCalendarConnectionFailureServiceTest {

  @Test
  @DisplayName("재연결 필요 오류는 credential을 제거해 sync error로 전환한 뒤 기존 token을 revoke한다")
  void givenConnectedConnection_whenPauseForReconnect_thenClearsCredentialsAndRevokesToken() {
    Instant occurredAt = Instant.parse("2026-09-21T00:00:00Z");
    GoogleCalendarConnection connection =
        new GoogleCalendarConnection(
            new GoogleCalendarIntegration(1L),
            "subject",
            "user@example.com",
            "encrypted-refresh-token",
            "encrypted-access-token",
            occurredAt.plusSeconds(3600),
            occurredAt.minusSeconds(60));
    connection.replaceNextSyncToken("sync-token");
    GoogleCalendarConnectionRepository connectionRepository =
        mock(GoogleCalendarConnectionRepository.class);
    when(connectionRepository.findWithIntegrationByAccountIdAndStateForUpdate(
            1L, GoogleCalendarConnectionState.CONNECTED))
        .thenReturn(Optional.of(connection));
    GoogleCalendarTokenRevocationService tokenRevocationService =
        mock(GoogleCalendarTokenRevocationService.class);
    GoogleCalendarConnectionFailureService service =
        new GoogleCalendarConnectionFailureService(
            new GoogleCalendarConnectionCommandService(connectionRepository),
            tokenRevocationService,
            new NoOpTransactionManager());

    service.pauseForReconnect(1L, "GOOGLE_CALENDAR_RECONNECT_REQUIRED", occurredAt);

    assertThat(connection.getState()).isEqualTo(GoogleCalendarConnectionState.SYNC_ERROR);
    assertThat(connection.getEncryptedRefreshToken()).isNull();
    assertThat(connection.getEncryptedAccessToken()).isNull();
    assertThat(connection.getAccessTokenExpiresAt()).isNull();
    assertThat(connection.getNextSyncToken()).isNull();
    assertThat(connection.getSyncErrorReason()).isEqualTo("GOOGLE_CALENDAR_RECONNECT_REQUIRED");
    verify(connectionRepository).saveAndFlush(connection);
    verify(tokenRevocationService).revokeSafely(1L, "encrypted-refresh-token");
  }

  private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {
    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}
  }
}
