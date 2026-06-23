package com.calio.calendar.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateRecurrenceOccurrenceRequest(
        String title,
        String description,
        Instant startAt,
        Instant endAt,
        Boolean isImportant
) {
}
