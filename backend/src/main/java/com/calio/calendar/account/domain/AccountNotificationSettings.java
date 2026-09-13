package com.calio.calendar.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalTime;
import java.util.Objects;

@Embeddable
public class AccountNotificationSettings {

    @Column(name = "calendar_notifications_enabled", nullable = false)
    private boolean calendarNotificationsEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "timed_reminder_offset", nullable = false)
    private TimedReminderOffset timedReminderOffset = TimedReminderOffset.MINUTES_10;

    @Enumerated(EnumType.STRING)
    @Column(name = "important_reminder_offset", nullable = false)
    private ImportantReminderOffset importantReminderOffset = ImportantReminderOffset.MINUTES_120;

    @Column(name = "all_day_reminder_time", nullable = false)
    private LocalTime allDayReminderTime = LocalTime.of(9, 0);

    @Column(name = "daily_briefing_enabled", nullable = false)
    private boolean dailyBriefingEnabled;

    @Column(name = "daily_briefing_time", nullable = false)
    private LocalTime dailyBriefingTime = LocalTime.of(8, 0);

    protected AccountNotificationSettings() {
    }

    AccountNotificationSettings(
            boolean calendarNotificationsEnabled,
            TimedReminderOffset timedReminderOffset,
            ImportantReminderOffset importantReminderOffset,
            LocalTime allDayReminderTime,
            boolean dailyBriefingEnabled,
            LocalTime dailyBriefingTime
    ) {
        this.calendarNotificationsEnabled = calendarNotificationsEnabled;
        this.timedReminderOffset = Objects.requireNonNull(timedReminderOffset);
        this.importantReminderOffset = Objects.requireNonNull(importantReminderOffset);
        this.allDayReminderTime = Objects.requireNonNull(allDayReminderTime);
        this.dailyBriefingEnabled = dailyBriefingEnabled;
        this.dailyBriefingTime = Objects.requireNonNull(dailyBriefingTime);
    }

    public boolean isCalendarNotificationsEnabled() {
        return calendarNotificationsEnabled;
    }

    public TimedReminderOffset getTimedReminderOffset() {
        return timedReminderOffset;
    }

    public ImportantReminderOffset getImportantReminderOffset() {
        return importantReminderOffset;
    }

    public LocalTime getAllDayReminderTime() {
        return allDayReminderTime;
    }

    public boolean isDailyBriefingEnabled() {
        return dailyBriefingEnabled;
    }

    public LocalTime getDailyBriefingTime() {
        return dailyBriefingTime;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AccountNotificationSettings settings)) {
            return false;
        }
        return calendarNotificationsEnabled == settings.calendarNotificationsEnabled
                && dailyBriefingEnabled == settings.dailyBriefingEnabled
                && timedReminderOffset == settings.timedReminderOffset
                && importantReminderOffset == settings.importantReminderOffset
                && allDayReminderTime.equals(settings.allDayReminderTime)
                && dailyBriefingTime.equals(settings.dailyBriefingTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                calendarNotificationsEnabled,
                timedReminderOffset,
                importantReminderOffset,
                allDayReminderTime,
                dailyBriefingEnabled,
                dailyBriefingTime
        );
    }
}
