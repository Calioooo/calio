package com.calio.calendar.integration.sync.operation.dto;

import com.calio.calendar.event.domain.Event;
import java.time.Instant;

public record GoogleEventJobPayload(
        String title,
        String description,
        Instant startAt,
        Instant endAt,
        boolean allDay,
        String timeZone
) {
    public static GoogleEventJobPayload from(Event event) {
        return new GoogleEventJobPayload(
                event.getTitle(), event.getDescription(), event.getStartAt(), event.getEndAt(),
                event.isAllDay(), event.getTimeZone()
        );
    }
}
