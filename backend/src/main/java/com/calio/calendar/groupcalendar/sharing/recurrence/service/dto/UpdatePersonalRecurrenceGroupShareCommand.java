package com.calio.calendar.groupcalendar.sharing.recurrence.service.dto;

import java.time.Instant;

public record UpdatePersonalRecurrenceGroupShareCommand(
        boolean showOriginalDetails,
        String overrideTitle,
        Instant overrideStartAt,
        Instant overrideEndAt,
        Boolean overrideAllDay
) {
}
