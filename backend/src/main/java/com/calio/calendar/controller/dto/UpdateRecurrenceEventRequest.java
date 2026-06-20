package com.calio.calendar.controller.dto;

import com.calio.calendar.repository.entity.RecurrenceFrequency;
import java.time.Instant;

public record UpdateRecurrenceEventRequest(
        NullableUpdateValue<String> title,
        NullableUpdateValue<String> description,
        Instant startAt,
        Instant endAt,
        RecurrenceFrequency recurrenceFrequency
) {
}
