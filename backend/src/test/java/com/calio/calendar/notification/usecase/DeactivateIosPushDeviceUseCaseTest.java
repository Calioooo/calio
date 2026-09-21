package com.calio.calendar.notification.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.notification.domain.IosPushDevice;
import com.calio.calendar.notification.repository.IosPushDeviceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeactivateIosPushDeviceUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");

  @Mock private IosPushDeviceRepository pushDeviceRepository;

  private DeactivateIosPushDeviceUseCase deactivateIosPushDeviceUseCase;

  @BeforeEach
  void setUp() {
    deactivateIosPushDeviceUseCase =
        new DeactivateIosPushDeviceUseCase(pushDeviceRepository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("등록된 설치를 해제하면 푸시 기기가 토큰을 반납하고 비활성화된다")
  void givenRegisteredPushDevice_whenDeactivate_thenReleasesToken() {
    // given
    IosPushDevice pushDevice = new IosPushDevice(1L, "installation", "token");
    when(pushDeviceRepository.findByAccountIdAndInstallationId(1L, "installation"))
        .thenReturn(Optional.of(pushDevice));

    // when
    deactivateIosPushDeviceUseCase.execute(1L, "installation");

    // then
    assertThat(pushDevice.canReceivePushNotifications()).isFalse();
    assertThat(pushDevice.getApnsToken()).isNull();
    verify(pushDeviceRepository).saveAndFlush(pushDevice);
  }
}
