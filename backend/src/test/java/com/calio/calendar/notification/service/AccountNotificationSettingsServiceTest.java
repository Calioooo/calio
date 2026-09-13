package com.calio.calendar.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.domain.AccountNotificationSettings;
import com.calio.calendar.account.domain.ImportantReminderOffset;
import com.calio.calendar.account.domain.TimedReminderOffset;
import com.calio.calendar.account.service.AccountCommandService;
import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.notification.controller.dto.UpdateNotificationSettingsRequest;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountNotificationSettingsServiceTest {

    @Mock
    private AccountQueryService accountQueryService;

    @Mock
    private AccountCommandService accountCommandService;

    private AccountNotificationSettingsService settingsService;

    @BeforeEach
    void setUp() {
        settingsService = new AccountNotificationSettingsService(accountQueryService, accountCommandService);
    }

    @Test
    @DisplayName("알림 설정 조회는 계정이 소유한 기본 정책을 반환한다")
    void givenAccountWithDefaultPolicy_whenGet_thenReturnsOwnedSettings() {
        // given
        Account account = new Account();
        when(accountQueryService.getAccount(1L)).thenReturn(account);

        // when
        AccountNotificationSettings settings = settingsService.get(1L);

        // then
        assertThat(settings).isSameAs(account.getNotificationSettings());
        assertThat(settings.getTimedReminderOffset()).isEqualTo(TimedReminderOffset.MINUTES_10);
        assertThat(settings.getImportantReminderOffset()).isEqualTo(ImportantReminderOffset.MINUTES_120);
    }

    @Test
    @DisplayName("알림 설정 변경을 계정 command service에 위임한다")
    void givenNotificationSettingsUpdate_whenUpdate_thenDelegatesToAccountCommandService() {
        // given
        Account account = new Account();
        UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest(
                true,
                TimedReminderOffset.NONE,
                ImportantReminderOffset.MINUTES_60,
                LocalTime.of(9, 0),
                false,
                LocalTime.of(8, 0)
        );
        AccountNotificationSettings expected = account.getNotificationSettings();
        when(accountQueryService.getAccount(1L)).thenReturn(account);
        when(accountCommandService.updateNotificationSettings(
                account,
                true,
                TimedReminderOffset.NONE,
                ImportantReminderOffset.MINUTES_60,
                LocalTime.of(9, 0),
                false,
                LocalTime.of(8, 0)
        )).thenReturn(expected);

        // when
        AccountNotificationSettings updated = settingsService.update(1L, request);

        // then
        assertThat(updated).isSameAs(expected);
        verify(accountCommandService).updateNotificationSettings(
                account,
                true,
                TimedReminderOffset.NONE,
                ImportantReminderOffset.MINUTES_60,
                LocalTime.of(9, 0),
                false,
                LocalTime.of(8, 0)
        );
    }
}
