package com.calio.calendar.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalTime;
import java.util.Objects;

@Embeddable
public record AccountNotificationSettings(
        @Column(name = "calendar_notifications_enabled", nullable = false)
        boolean calendarNotificationsEnabled,

        @Enumerated(EnumType.STRING)
        @Column(name = "timed_reminder_offset", nullable = false)
        TimedReminderOffset timedReminderOffset,

        @Enumerated(EnumType.STRING)
        @Column(name = "important_reminder_offset", nullable = false)
        ImportantReminderOffset importantReminderOffset,

        @Column(name = "all_day_reminder_time", nullable = false)
        LocalTime allDayReminderTime,

        @Column(name = "daily_briefing_enabled", nullable = false)
        boolean dailyBriefingEnabled,

        @Column(name = "daily_briefing_time", nullable = false)
        LocalTime dailyBriefingTime
) {

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
                LocalTime.of(8, 0)
        );
    }
}
