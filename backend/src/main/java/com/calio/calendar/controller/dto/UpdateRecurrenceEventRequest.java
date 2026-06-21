package com.calio.calendar.controller.dto;

import com.calio.calendar.repository.entity.RecurrenceFrequency;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateRecurrenceEventRequest(
        String title,
        String description,
        Instant startAt,
        Instant endAt,
        RecurrenceFrequency recurrenceFrequency
) {
}
