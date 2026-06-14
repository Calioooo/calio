package com.calio.calendar.controller.dto;

import com.calio.calendar.repository.entity.Event;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record EventResponse(
        Long id,
        String title,
        String description,
        Instant startAt,
        Instant endAt,
        boolean importantEvent,
        Long recurrenceId,
        @JsonProperty("isRecurrenceOccurrence")
        boolean isRecurrenceOccurrence,
        Instant createdAt,
        Instant updatedAt
) {

    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getStartAt(),
                event.getEndAt(),
                event.importantEvent(),
                event.getRecurrenceId().orElse(null),
                event.isRecurrenceOccurrence(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}
