package com.calio.calendar.notification.controller.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.Set;

public record UpdateNotificationSettingsRequest(
        @NotNull Boolean calendarNotificationsEnabled,
        @Min(0) @Max(1440) Integer timedReminderMinutes,
        @Min(0) @Max(1440) Integer importantReminderMinutes,
        @NotNull LocalTime allDayReminderTime,
        @NotNull Boolean dailyBriefingEnabled,
        @NotNull LocalTime dailyBriefingTime
) {
    @AssertTrue(message = "시간 일정과 중요 일정 알림 시간은 지원되는 값이어야 합니다.")
    public boolean hasSupportedReminderMinutes() {
        return isTimedReminderMinutesSupported() && isImportantReminderMinutesSupported();
    }

    private boolean isTimedReminderMinutesSupported() {
        return timedReminderMinutes == null
                || Set.of(0, 5, 10, 30, 60, 120, 1440).contains(timedReminderMinutes);
    }

    private boolean isImportantReminderMinutesSupported() {
        return importantReminderMinutes == null
                || Set.of(30, 60, 120, 1440).contains(importantReminderMinutes);
    }
}
