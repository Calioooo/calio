package com.calio.calendar.notification.usecase;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateNotificationSettingsUseCaseTest {

  @Mock private AccountRepository accountRepository;

  @InjectMocks private UpdateNotificationSettingsUseCase updateNotificationSettingsUseCase;

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
    AccountNotificationSettings updated = updateNotificationSettingsUseCase.execute(1L, request);

    // then
    assertThat(updated.timedReminderOffset()).isEqualTo(TimedReminderOffset.NONE);
    assertThat(updated.importantReminderOffset()).isEqualTo(ImportantReminderOffset.MINUTES_60);
    verify(accountRepository).saveAndFlush(account);
  }
}
