package com.calio.calendar.account.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountNotificationSettingsTest {

    @Test
    @DisplayName("새 계정은 기본 알림 정책 VO를 함께 가진다")
    void givenNewAccount_whenGetNotificationSettings_thenReturnsDefaultPolicy() {
        // given
        Account account = new Account();

        // when
        AccountNotificationSettings settings = account.getNotificationSettings();

        // then
        assertThat(settings.isCalendarNotificationsEnabled()).isTrue();
        assertThat(settings.getTimedReminderOffset()).isEqualTo(TimedReminderOffset.MINUTES_10);
        assertThat(settings.getImportantReminderOffset()).isEqualTo(ImportantReminderOffset.MINUTES_120);
        assertThat(settings.getAllDayReminderTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(settings.isDailyBriefingEnabled()).isFalse();
        assertThat(settings.getDailyBriefingTime()).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    @DisplayName("계정이 알림 정책을 변경하면 새 설정 VO로 교체한다")
    void givenNotificationPolicy_whenUpdate_thenReplacesOwnedValueObject() {
        // given
        Account account = new Account();
        AccountNotificationSettings previousSettings = account.getNotificationSettings();

        // when
        account.updateNotificationSettings(
                false,
                TimedReminderOffset.NONE,
                ImportantReminderOffset.MINUTES_60,
                LocalTime.of(10, 0),
                true,
                LocalTime.of(7, 30)
        );

        // then
        AccountNotificationSettings updatedSettings = account.getNotificationSettings();
        assertThat(updatedSettings).isNotSameAs(previousSettings);
        assertThat(updatedSettings.isCalendarNotificationsEnabled()).isFalse();
        assertThat(updatedSettings.getTimedReminderOffset()).isEqualTo(TimedReminderOffset.NONE);
        assertThat(updatedSettings.getImportantReminderOffset()).isEqualTo(ImportantReminderOffset.MINUTES_60);
        assertThat(updatedSettings.getAllDayReminderTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(updatedSettings.isDailyBriefingEnabled()).isTrue();
        assertThat(updatedSettings.getDailyBriefingTime()).isEqualTo(LocalTime.of(7, 30));
    }
}
