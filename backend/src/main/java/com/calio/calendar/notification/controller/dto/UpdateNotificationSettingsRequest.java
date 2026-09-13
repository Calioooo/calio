package com.calio.calendar.notification.controller.dto;

import com.calio.calendar.notification.domain.ImportantReminderOffset;
import com.calio.calendar.notification.domain.TimedReminderOffset;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record UpdateNotificationSettingsRequest(
        @NotNull(message = "일정 알림 사용 여부는 필수입니다.") Boolean calendarNotificationsEnabled,
        @NotNull(message = "시간 일정 알림 시각은 필수입니다.") TimedReminderOffset timedReminderOffset,
        @NotNull(message = "중요 일정 추가 알림 시각은 필수입니다.") ImportantReminderOffset importantReminderOffset,
        @NotNull(message = "종일 일정 알림 시각은 필수입니다.") LocalTime allDayReminderTime,
        @NotNull(message = "일일 브리핑 사용 여부는 필수입니다.") Boolean dailyBriefingEnabled,
        @NotNull(message = "일일 브리핑 시각은 필수입니다.") LocalTime dailyBriefingTime
) {
}
