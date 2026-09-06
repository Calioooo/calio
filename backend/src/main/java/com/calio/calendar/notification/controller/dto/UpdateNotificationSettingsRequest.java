package com.calio.calendar.notification.controller.dto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
public record UpdateNotificationSettingsRequest(@NotNull Boolean calendarNotificationsEnabled, @Min(0) @Max(1440) Integer timedReminderMinutes, @Min(0) @Max(1440) Integer importantReminderMinutes, @NotNull LocalTime allDayReminderTime, @NotNull Boolean dailyBriefingEnabled, @NotNull LocalTime dailyBriefingTime) { }
