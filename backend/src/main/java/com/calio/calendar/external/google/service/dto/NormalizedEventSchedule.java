package com.calio.calendar.external.google.service.dto;

import java.time.Instant;

public record NormalizedEventSchedule(
        Instant startAt,
        Instant endAt,
        boolean allDay,
        String timeZone
) {
}
