package com.calio.calendar.groupcalendar.event.service.dto;

import java.time.Instant;

public record GroupCalendarEventCommand(
        String title,
        String description,
        Instant startAt,
        Instant endAt,
        boolean allDay,
        String timeZone,
        Long tagId
) {
}
