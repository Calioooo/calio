package com.calio.calendar.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.domain.AccountNotificationSettings;
import com.calio.calendar.account.domain.ImportantReminderOffset;
import com.calio.calendar.account.domain.TimedReminderOffset;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.notification.controller.dto.UpdateNotificationSettingsRequest;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountNotificationSettingsServiceTest {

  @Mock private AccountRepository accountRepository;

  private AccountNotificationSettingsService settingsService;

  @BeforeEach
  void setUp() {
    settingsService = new AccountNotificationSettingsService(accountRepository);
  }

  @Test
  @DisplayName("알림 설정 조회는 계정이 소유한 기본 정책을 반환한다")
  void givenAccountWithDefaultPolicy_whenGet_thenReturnsOwnedSettings() {
    // given
    Account account = new Account();
    when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

    // when
    AccountNotificationSettings settings = settingsService.get(1L);

    // then
    assertThat(settings).isSameAs(account.getNotificationSettings());
    assertThat(settings.timedReminderOffset()).isEqualTo(TimedReminderOffset.MINUTES_10);
    assertThat(settings.importantReminderOffset()).isEqualTo(ImportantReminderOffset.MINUTES_120);
  }

  @Test
  @DisplayName("알림 설정 변경은 계정의 설정 VO를 교체하고 저장한다")
  void givenNotificationSettingsUpdate_whenUpdate_thenChangesOwnedValueObject() {
    // given
    Account account = new Account();
    UpdateNotificationSettingsRequest request =
        new UpdateNotificationSettingsRequest(
            true,
            TimedReminderOffset.NONE,
            ImportantReminderOffset.MINUTES_60,
            LocalTime.of(9, 0),
            false,
            LocalTime.of(8, 0));
    when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
    when(accountRepository.saveAndFlush(account)).thenReturn(account);

    // when
    AccountNotificationSettings updated = settingsService.update(1L, request);

    // then
    assertThat(updated.timedReminderOffset()).isEqualTo(TimedReminderOffset.NONE);
    assertThat(updated.importantReminderOffset()).isEqualTo(ImportantReminderOffset.MINUTES_60);
    verify(accountRepository).saveAndFlush(account);
  }
}
