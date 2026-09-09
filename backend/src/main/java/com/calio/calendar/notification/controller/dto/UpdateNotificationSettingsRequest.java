package com.calio.calendar.notification.controller.dto;

import com.calio.calendar.notification.domain.ImportantReminderOffset;
import com.calio.calendar.notification.domain.TimedReminderOffset;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record UpdateNotificationSettingsRequest(
        @NotNull Boolean calendarNotificationsEnabled,
        @NotNull TimedReminderOffset timedReminderOffset,
        @NotNull ImportantReminderOffset importantReminderOffset,
        @NotNull LocalTime allDayReminderTime,
        @NotNull Boolean dailyBriefingEnabled,
        @NotNull LocalTime dailyBriefingTime
) {
}
