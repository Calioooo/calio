package com.calio.calendar.notification.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public record NotificationScheduleKey(String value) {

    public NotificationScheduleKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Notification schedule key must not be blank.");
        }
    }

    public static NotificationScheduleKey personalEvent(Long eventId) {
        return new NotificationScheduleKey("personal:" + Objects.requireNonNull(eventId));
    }

    public static NotificationScheduleKey personalRecurrence(Long recurrenceId, Instant originStartAt) {
        return new NotificationScheduleKey(
                "personal-recurrence:"
                        + Objects.requireNonNull(recurrenceId)
                        + ":"
                        + Objects.requireNonNull(originStartAt)
        );
    }

    public static NotificationScheduleKey groupEvent(Long eventId) {
        return new NotificationScheduleKey("group:" + Objects.requireNonNull(eventId));
    }

    public static NotificationScheduleKey groupRecurrence(Long recurrenceId, Instant originStartAt) {
        return new NotificationScheduleKey(
                "group-recurrence:"
                        + Objects.requireNonNull(recurrenceId)
                        + ":"
                        + Objects.requireNonNull(originStartAt)
        );
    }

    public static NotificationScheduleKey briefing(LocalDate targetDate) {
        return new NotificationScheduleKey("briefing:" + Objects.requireNonNull(targetDate));
    }
}
