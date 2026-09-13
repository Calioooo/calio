package com.calio.calendar.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.notification.controller.dto.UpdateNotificationSettingsRequest;
import com.calio.calendar.notification.domain.AccountNotificationSettings;
import com.calio.calendar.notification.domain.ImportantReminderOffset;
import com.calio.calendar.notification.domain.TimedReminderOffset;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class AccountNotificationSettingsServiceTest {

    @Mock
    private AccountQueryService accountQueryService;

    @Mock
    private AccountNotificationSettingsQueryService settingsQueryService;

    @Mock
    private AccountNotificationSettingsCommandService settingsCommandService;

    private AccountNotificationSettingsService settingsService;

    @BeforeEach
    void setUp() {
        settingsService = new AccountNotificationSettingsService(
                accountQueryService,
                settingsQueryService,
                settingsCommandService
        );
    }

    @Test
    @DisplayName("동시에 처음 조회해도 계정별 기본 알림 설정 하나를 반환한다")
    void givenConcurrentDefaultCreation_whenGet_thenReturnsPersistedSettings() {
        // given
        Account account = new Account();
        AccountNotificationSettings settings = new AccountNotificationSettings(account);
        when(settingsQueryService.getSettingsIfExists(1L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(settings));
        when(accountQueryService.getAccount(1L)).thenReturn(account);
        when(settingsCommandService.createDefaultSettings(account))
                .thenThrow(new DataIntegrityViolationException("duplicate account"));

        // when
        AccountNotificationSettings result = settingsService.get(1L);

        // then
        assertThat(result).isSameAs(settings);
        verify(settingsCommandService).createDefaultSettings(account);
    }

    @Test
    @DisplayName("알림 설정 변경을 command service에 위임한다")
    void givenNotificationSettingsUpdate_whenUpdate_thenDelegatesToCommandService() {
        // given
        AccountNotificationSettings settings = new AccountNotificationSettings(new Account());
        when(settingsQueryService.getSettingsIfExists(1L)).thenReturn(Optional.of(settings));
        UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest(
                true,
                TimedReminderOffset.NONE,
                ImportantReminderOffset.MINUTES_60,
                LocalTime.of(9, 0),
                false,
                LocalTime.of(8, 0)
        );
        when(settingsCommandService.update(settings, request)).thenReturn(settings);

        // when
        AccountNotificationSettings updated = settingsService.update(1L, request);

        // then
        assertThat(updated).isSameAs(settings);
        verify(settingsCommandService).update(settings, request);
    }
}
