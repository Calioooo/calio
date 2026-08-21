package com.calio.calendar.groupcalendar.recurrence.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record GroupCalendarRecurrenceOccurrenceRequest(
        @NotNull Instant originStartAt,
        @NotBlank String title,
        String description,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @NotNull Boolean allDay,
        String timeZone
) {
}
