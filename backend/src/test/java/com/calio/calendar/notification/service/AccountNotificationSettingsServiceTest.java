package com.calio.calendar.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("시간 일정과 중요 일정 offset은 서로 독립적으로 갱신한다")
    void givenIndependentReminderOffsets_whenUpdate_thenKeepsEachConfiguredValue() {
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

        // when
        AccountNotificationSettings updated = settingsService.update(1L, request);

        // then
        assertThat(updated.getTimedReminderOffset()).isEqualTo(TimedReminderOffset.NONE);
        assertThat(updated.getImportantReminderOffset()).isEqualTo(ImportantReminderOffset.MINUTES_60);
    }
}
