package com.calio.calendar.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.notification.domain.IosPushDevice;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:ios-push-device-repository-test;MODE=MySQL",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
class IosPushDeviceRepositoryIntegrationTest {

  @Autowired private AccountRepository accountRepository;

  @Autowired private IosPushDeviceRepository pushDeviceRepository;

  @Test
  @DisplayName("예상한 APNs 토큰이 이미 교체됐으면 새 토큰을 비활성화하지 않는다")
  void givenTokenWasReplaced_whenDeactivatingStaleToken_thenPreservesCurrentToken() {
    // given
    Account account = accountRepository.saveAndFlush(new Account());
    IosPushDevice pushDevice =
        pushDeviceRepository.saveAndFlush(
            new IosPushDevice(account.getId(), "installation", "previous-token"));
    pushDevice.refresh("current-token");
    pushDeviceRepository.saveAndFlush(pushDevice);

    // when
    int updated =
        pushDeviceRepository.deactivateIfTokenMatches(
            pushDevice.getId(), "previous-token", Instant.parse("2026-09-21T00:00:00Z"));

    // then
    assertThat(updated).isZero();
    assertThat(pushDeviceRepository.findById(pushDevice.getId()))
        .get()
        .satisfies(
            current -> {
              assertThat(current.getApnsToken()).isEqualTo("current-token");
              assertThat(current.canReceivePushNotifications()).isTrue();
            });
  }
}
