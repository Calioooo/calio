package com.calio.calendar.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

@Embeddable
public record AccountNotificationSettings(
    @Column(name = "calendar_notifications_enabled", nullable = false)
        boolean calendarNotificationsEnabled,
    @Enumerated(EnumType.STRING) @Column(name = "timed_reminder_offset", nullable = false)
        TimedReminderOffset timedReminderOffset,
    @Enumerated(EnumType.STRING) @Column(name = "important_reminder_offset", nullable = false)
        ImportantReminderOffset importantReminderOffset,
    @Column(name = "all_day_reminder_time", nullable = false) LocalTime allDayReminderTime,
    @Column(name = "daily_briefing_enabled", nullable = false) boolean dailyBriefingEnabled,
    @Column(name = "daily_briefing_time", nullable = false) LocalTime dailyBriefingTime) {

  public AccountNotificationSettings {
    Objects.requireNonNull(timedReminderOffset);
    Objects.requireNonNull(importantReminderOffset);
    Objects.requireNonNull(allDayReminderTime);
    Objects.requireNonNull(dailyBriefingTime);
  }

  public static AccountNotificationSettings defaults() {
    return new AccountNotificationSettings(
        true,
        TimedReminderOffset.MINUTES_10,
        ImportantReminderOffset.MINUTES_120,
        LocalTime.of(9, 0),
        false,
        LocalTime.of(8, 0));
  }

  public Optional<Instant> timedReminderAt(Instant startAt) {
    Objects.requireNonNull(startAt);
    if (timedReminderOffset.isDisabled()) {
      return Optional.empty();
    }
    return Optional.of(startAt.minus(Duration.ofMinutes(timedReminderOffset.minutes())));
  }

  public Optional<Instant> importantReminderAt(Instant startAt) {
    Objects.requireNonNull(startAt);
    if (importantReminderOffset.isDisabled()) {
      return Optional.empty();
    }
    return Optional.of(startAt.minus(Duration.ofMinutes(importantReminderOffset.minutes())));
  }

  public Instant allDayReminderAt(LocalDate startDate, ZoneId policyZone) {
    return startDate.atTime(allDayReminderTime).atZone(policyZone).toInstant();
  }

  public Optional<Instant> dailyBriefingAt(LocalDate targetDate, ZoneId policyZone) {
    if (!dailyBriefingEnabled) {
      return Optional.empty();
    }
    return Optional.of(targetDate.atTime(dailyBriefingTime).atZone(policyZone).toInstant());
  }
}
