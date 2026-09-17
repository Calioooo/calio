package com.calio.calendar.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IosPushDeviceTest {

  @Test
  @DisplayName("푸시 기기는 자신이 속한 계정과 설치본을 함께 판별한다")
  void givenAccountAndInstallation_whenCheckOwnership_thenMatchesBothValues() {
    // given
    IosPushDevice pushDevice = new IosPushDevice(1L, "installation", "token");

    // when & then
    assertThat(pushDevice.belongsToInstallation(1L, "installation")).isTrue();
    assertThat(pushDevice.belongsToInstallation(2L, "installation")).isFalse();
    assertThat(pushDevice.belongsToInstallation(1L, "other-installation")).isFalse();
  }

  @Test
  @DisplayName("푸시 기기를 해제하면 비활성화와 APNs 토큰 제거를 함께 수행한다")
  void givenActivePushDevice_whenDeactivate_thenCannotReceivePush() {
    // given
    IosPushDevice pushDevice = new IosPushDevice(1L, "installation", "token");

    // when
    pushDevice.deactivate(Instant.parse("2026-09-08T00:00:00Z"));

    // then
    assertThat(pushDevice.isEligible()).isFalse();
    assertThat(pushDevice.getApnsToken()).isNull();
  }
}
