package com.calio.calendar.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.notification.controller.dto.UpdateNotificationSettingsRequest;
import com.calio.calendar.notification.domain.AccountNotificationSettings;
import com.calio.calendar.notification.domain.ImportantReminderOffset;
import com.calio.calendar.notification.domain.TimedReminderOffset;
import com.calio.calendar.notification.repository.AccountNotificationSettingsRepository;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountNotificationSettingsCommandServiceTest {

    @Mock
    private AccountNotificationSettingsRepository settingsRepository;

    private AccountNotificationSettingsCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new AccountNotificationSettingsCommandService(settingsRepository);
    }

    @Test
    @DisplayName("알림 설정 변경 command는 요청한 정책을 엔티티에 반영하고 저장한다")
    void givenNotificationPolicy_whenUpdate_thenChangesAndSavesSettings() {
        // given
        AccountNotificationSettings settings = new AccountNotificationSettings(new Account());
        UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest(
                true,
                TimedReminderOffset.NONE,
                ImportantReminderOffset.MINUTES_60,
                LocalTime.of(10, 0),
                true,
                LocalTime.of(7, 30)
        );
        when(settingsRepository.saveAndFlush(settings)).thenReturn(settings);

        // when
        AccountNotificationSettings updated = commandService.update(settings, request);

        // then
        assertThat(updated.getTimedReminderOffset()).isEqualTo(TimedReminderOffset.NONE);
        assertThat(updated.getImportantReminderOffset()).isEqualTo(ImportantReminderOffset.MINUTES_60);
        assertThat(updated.getAllDayReminderTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(updated.isDailyBriefingEnabled()).isTrue();
        assertThat(updated.getDailyBriefingTime()).isEqualTo(LocalTime.of(7, 30));
    }
}
