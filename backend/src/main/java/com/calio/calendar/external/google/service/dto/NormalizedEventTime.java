package com.calio.calendar.external.google.service.dto;

import java.time.Instant;

public record NormalizedEventTime(
        Instant instant,
        boolean allDay,
        String timeZone
) {
}
