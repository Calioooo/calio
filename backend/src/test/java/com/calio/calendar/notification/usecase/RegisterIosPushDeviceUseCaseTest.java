package com.calio.calendar.notification.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class RegisterIosPushDeviceUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");

  @Mock private IosPushDeviceRepository pushDeviceRepository;

  private RegisterIosPushDeviceUseCase registerIosPushDeviceUseCase;

  @BeforeEach
  void setUp() {
    registerIosPushDeviceUseCase =
        new RegisterIosPushDeviceUseCase(pushDeviceRepository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("다른 설치가 가진 토큰을 등록하면 이전 푸시 기기를 비활성화하고 새 설치에 토큰을 연결한다")
  void givenTokenOwnedByAnotherInstallation_whenRegister_thenTransfersTokenOwnership() {
    // given
    IosPushDevice previousPushDevice = new IosPushDevice(2L, "previous-installation", "token");
    when(pushDeviceRepository.lockDeviceWithToken("token"))
        .thenReturn(Optional.of(previousPushDevice));
    when(pushDeviceRepository.findByAccountIdAndInstallationId(1L, "installation"))
        .thenReturn(Optional.empty());

    // when
    registerIosPushDeviceUseCase.execute(1L, "installation", "token");

    // then
    assertThat(previousPushDevice.canReceivePushNotifications()).isFalse();
    assertThat(previousPushDevice.getApnsToken()).isNull();
    ArgumentCaptor<IosPushDevice> deviceCaptor = ArgumentCaptor.forClass(IosPushDevice.class);
    verify(pushDeviceRepository, times(2)).saveAndFlush(deviceCaptor.capture());
    IosPushDevice registeredPushDevice = deviceCaptor.getAllValues().get(1);
    assertThat(registeredPushDevice.canReceivePushNotifications()).isTrue();
    assertThat(registeredPushDevice.getApnsToken()).isEqualTo("token");
  }

  @Test
  @DisplayName("동시 등록으로 APNs 토큰 소유권이 충돌하면 명시적인 conflict 오류를 반환한다")
  void givenConcurrentTokenRegistration_whenRegister_thenThrowsTokenConflict() {
    // given
    when(pushDeviceRepository.lockDeviceWithToken("token")).thenReturn(Optional.empty());
    when(pushDeviceRepository.findByAccountIdAndInstallationId(1L, "installation"))
        .thenReturn(Optional.empty());
    when(pushDeviceRepository.saveAndFlush(any(IosPushDevice.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate token"));

    // when & then
    assertThatThrownBy(() -> registerIosPushDeviceUseCase.execute(1L, "installation", "token"))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.NOTIFICATION_ENDPOINT_TOKEN_CONFLICT));
  }
}
