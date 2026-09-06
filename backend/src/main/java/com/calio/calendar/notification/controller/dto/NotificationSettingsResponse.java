package com.calio.calendar.notification.controller.dto;
import com.calio.calendar.notification.domain.AccountNotificationSettings;
import java.time.LocalTime;
public record NotificationSettingsResponse(boolean calendarNotificationsEnabled, Integer timedReminderMinutes, Integer importantReminderMinutes, LocalTime allDayReminderTime, boolean dailyBriefingEnabled, LocalTime dailyBriefingTime) {
    public static NotificationSettingsResponse from(AccountNotificationSettings settings) { return new NotificationSettingsResponse(settings.isCalendarNotificationsEnabled(), settings.getTimedReminderMinutes(), settings.getImportantReminderMinutes(), settings.getAllDayReminderTime(), settings.isDailyBriefingEnabled(), settings.getDailyBriefingTime()); }
}
