package com.calio.calendar.notification.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.domain.AccountNotificationSettings;
import com.calio.calendar.account.domain.ImportantReminderOffset;
import com.calio.calendar.account.domain.TimedReminderOffset;
import com.calio.calendar.account.repository.AccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetNotificationSettingsUseCaseTest {

  @Mock private AccountRepository accountRepository;

  @InjectMocks private GetNotificationSettingsUseCase getNotificationSettingsUseCase;

  @Test
  @DisplayName("알림 설정 조회는 계정이 소유한 기본 정책을 반환한다")
  void givenAccountWithDefaultPolicy_whenGet_thenReturnsOwnedSettings() {
    // given
    Account account = new Account();
    when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

    // when
    AccountNotificationSettings settings = getNotificationSettingsUseCase.execute(1L);

    // then
    assertThat(settings).isSameAs(account.getNotificationSettings());
    assertThat(settings.timedReminderOffset()).isEqualTo(TimedReminderOffset.MINUTES_10);
    assertThat(settings.importantReminderOffset()).isEqualTo(ImportantReminderOffset.MINUTES_120);
  }
}
