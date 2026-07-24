package com.calio.calendar.recurrence.domain;

import java.time.Instant;

public record RecurrenceOccurrence(
        Instant originStartAt,
        Instant startAt,
        Instant endAt
) {
}
