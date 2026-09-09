package com.calio.calendar.notification.controller.dto;

import com.calio.calendar.notification.domain.AccountNotificationSettings;
import com.calio.calendar.notification.domain.ImportantReminderOffset;
import com.calio.calendar.notification.domain.TimedReminderOffset;
import java.time.LocalTime;

public record NotificationSettingsResponse(
        boolean calendarNotificationsEnabled,
        TimedReminderOffset timedReminderOffset,
        ImportantReminderOffset importantReminderOffset,
        LocalTime allDayReminderTime,
        boolean dailyBriefingEnabled,
        LocalTime dailyBriefingTime
) {

    public static NotificationSettingsResponse from(AccountNotificationSettings settings) {
        return new NotificationSettingsResponse(
                settings.isCalendarNotificationsEnabled(),
                settings.getTimedReminderOffset(),
                settings.getImportantReminderOffset(),
                settings.getAllDayReminderTime(),
                settings.isDailyBriefingEnabled(),
                settings.getDailyBriefingTime()
        );
    }
}
