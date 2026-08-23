package com.calio.calendar.groupcalendar.sharing.recurrence.service.dto;

import java.time.Instant;

public record UpdatePersonalRecurrenceGroupShareOccurrenceOverrideCommand(
        Instant originStartAt,
        String overrideTitle,
        Instant overrideStartAt,
        Instant overrideEndAt,
        Boolean overrideAllDay
) {
}
