package com.calio.calendar.account.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
    assertThat(settings.calendarNotificationsEnabled()).isTrue();
    assertThat(settings.timedReminderOffset()).isEqualTo(TimedReminderOffset.MINUTES_10);
    assertThat(settings.importantReminderOffset()).isEqualTo(ImportantReminderOffset.MINUTES_120);
    assertThat(settings.allDayReminderTime()).isEqualTo(LocalTime.of(9, 0));
    assertThat(settings.dailyBriefingEnabled()).isFalse();
    assertThat(settings.dailyBriefingTime()).isEqualTo(LocalTime.of(8, 0));
  }

  @Test
  @DisplayName("계정이 알림 정책을 변경하면 새 설정 VO로 교체한다")
  void givenNotificationPolicy_whenUpdate_thenReplacesOwnedValueObject() {
    // given
    Account account = new Account();
    AccountNotificationSettings previousSettings = account.getNotificationSettings();

    // when
    account.changeNotificationSettings(
        new AccountNotificationSettings(
            false,
            TimedReminderOffset.NONE,
            ImportantReminderOffset.MINUTES_60,
            LocalTime.of(10, 0),
            true,
            LocalTime.of(7, 30)));

    // then
    AccountNotificationSettings updatedSettings = account.getNotificationSettings();
    assertThat(updatedSettings).isNotSameAs(previousSettings);
    assertThat(updatedSettings.calendarNotificationsEnabled()).isFalse();
    assertThat(updatedSettings.timedReminderOffset()).isEqualTo(TimedReminderOffset.NONE);
    assertThat(updatedSettings.importantReminderOffset())
        .isEqualTo(ImportantReminderOffset.MINUTES_60);
    assertThat(updatedSettings.allDayReminderTime()).isEqualTo(LocalTime.of(10, 0));
    assertThat(updatedSettings.dailyBriefingEnabled()).isTrue();
    assertThat(updatedSettings.dailyBriefingTime()).isEqualTo(LocalTime.of(7, 30));
  }

  @Test
  @DisplayName("꺼진 시간 일정과 중요 일정 알림은 발송 시각을 만들지 않는다")
  void givenDisabledReminderOffsets_whenResolveReminderAt_thenReturnsEmpty() {
    // given
    AccountNotificationSettings settings =
        new AccountNotificationSettings(
            true,
            TimedReminderOffset.NONE,
            ImportantReminderOffset.NONE,
            LocalTime.of(9, 0),
            false,
            LocalTime.of(8, 0));
    Instant startAt = Instant.parse("2026-09-08T03:00:00Z");

    // when & then
    assertThat(settings.timedReminderAt(startAt)).isEmpty();
    assertThat(settings.importantReminderAt(startAt)).isEmpty();
  }

  @Test
  @DisplayName("알림 설정은 일정 시작 시각에서 사용자 offset을 역산한다")
  void givenReminderOffsets_whenResolveReminderAt_thenCalculatesDueInstants() {
    // given
    AccountNotificationSettings settings = AccountNotificationSettings.defaults();
    Instant startAt = Instant.parse("2026-09-08T03:00:00Z");

    // when & then
    assertThat(settings.timedReminderAt(startAt)).contains(Instant.parse("2026-09-08T02:50:00Z"));
    assertThat(settings.importantReminderAt(startAt))
        .contains(Instant.parse("2026-09-08T01:00:00Z"));
  }

  @Test
  @DisplayName("종일 일정과 브리핑은 Seoul 기준 사용자가 선택한 시각으로 계산한다")
  void givenDailyTimes_whenResolveDueAt_thenUsesPolicyZone() {
    // given
    AccountNotificationSettings settings =
        new AccountNotificationSettings(
            true,
            TimedReminderOffset.MINUTES_10,
            ImportantReminderOffset.MINUTES_120,
            LocalTime.of(9, 0),
            true,
            LocalTime.of(8, 0));
    LocalDate targetDate = LocalDate.of(2026, 9, 8);
    ZoneId policyZone = ZoneId.of("Asia/Seoul");

    // when & then
    assertThat(settings.allDayReminderAt(targetDate, policyZone))
        .isEqualTo(Instant.parse("2026-09-08T00:00:00Z"));
    assertThat(settings.dailyBriefingAt(targetDate, policyZone))
        .contains(Instant.parse("2026-09-07T23:00:00Z"));
  }
}
