package com.calio.calendar.groupcalendar.recurrence.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public record GroupCalendarRecurrenceRequest(
        @NotBlank String title,
        String description,
        @NotNull Boolean allDay,
        @NotNull Instant firstOccurrenceStartAt,
        @NotNull Instant firstOccurrenceEndAt,
        String timeZone,
        @NotNull List<String> recurrence,
        Long tagId
) {
}
