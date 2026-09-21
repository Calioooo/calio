package com.calio.calendar.notification.domain;

import java.time.LocalDate;
import java.util.Objects;

public record CalendarNotificationContent(
    CalendarNotificationType type, LocalDate targetDate, String body) {

  public CalendarNotificationContent {
    Objects.requireNonNull(type);
    Objects.requireNonNull(targetDate);
    if (body == null || body.isBlank()) {
      throw new IllegalArgumentException("Notification body must not be blank.");
    }
  }

  public static CalendarNotificationContent schedule(
      CalendarNotificationType type, LocalDate targetDate, String title, String groupName) {
    if (type == CalendarNotificationType.BRIEFING) {
      throw new IllegalArgumentException("Briefing content requires a schedule count.");
    }
    Objects.requireNonNull(title);
    String body = groupName == null ? title : title + " · " + groupName;
    return new CalendarNotificationContent(type, targetDate, body);
  }

  public static CalendarNotificationContent briefing(LocalDate targetDate, long scheduleCount) {
    if (scheduleCount <= 0) {
      throw new IllegalArgumentException("Briefing schedule count must be positive.");
    }
    return new CalendarNotificationContent(
        CalendarNotificationType.BRIEFING, targetDate, "오늘 일정이 " + scheduleCount + "개 있어요");
  }
}
