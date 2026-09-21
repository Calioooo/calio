package com.calio.calendar.integration.connection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnectionState;
import com.calio.calendar.integration.connection.repository.GoogleCalendarConnectionRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarConnectionCommandServiceTest {

  private static final Long INTEGRATION_ID = 10L;

  private final GoogleCalendarConnectionRepository connectionRepository =
      mock(GoogleCalendarConnectionRepository.class);
  private final GoogleCalendarConnectionCommandService service =
      new GoogleCalendarConnectionCommandService(connectionRepository);

  @Test
  @DisplayName("연결 해제 대상은 retained sync error보다 현재 connected Connection을 우선한다")
  void givenConnectedAndSyncErrorConnections_whenLockDisconnectable_thenReturnsConnected() {
    GoogleCalendarConnection connected = mock(GoogleCalendarConnection.class);
    when(connectionRepository.findWithIntegrationByIntegrationIdAndStateForUpdate(
            INTEGRATION_ID, GoogleCalendarConnectionState.CONNECTED))
        .thenReturn(Optional.of(connected));

    assertThat(service.tryLockDisconnectableConnectionByIntegration(INTEGRATION_ID))
        .contains(connected);

    verify(connectionRepository, never())
        .findFirstByIntegration_IdAndStateOrderBySyncErrorAtDescIdDesc(
            INTEGRATION_ID, GoogleCalendarConnectionState.SYNC_ERROR);
  }

  @Test
  @DisplayName("connected Connection이 없으면 가장 최근 sync error Connection을 선택한다")
  void givenOnlySyncErrorConnections_whenLockDisconnectable_thenReturnsLatestSyncError() {
    GoogleCalendarConnection syncError = mock(GoogleCalendarConnection.class);
    when(connectionRepository.findWithIntegrationByIntegrationIdAndStateForUpdate(
            INTEGRATION_ID, GoogleCalendarConnectionState.CONNECTED))
        .thenReturn(Optional.empty());
    when(connectionRepository.findFirstByIntegration_IdAndStateOrderBySyncErrorAtDescIdDesc(
            INTEGRATION_ID, GoogleCalendarConnectionState.SYNC_ERROR))
        .thenReturn(Optional.of(syncError));

    assertThat(service.tryLockDisconnectableConnectionByIntegration(INTEGRATION_ID))
        .contains(syncError);
  }
}
