package com.calio.calendar.controller.dto;

import com.calio.calendar.repository.entity.Event;
import java.time.Instant;

public record EventResponse(
        Long id,
        String title,
        String description,
        Instant startAt,
        Instant endAt,
        boolean importantEvent,
        Long recurrenceId,
        boolean isRecurrenceOccurrence,
        TagResponse tag,
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
                TagResponse.from(event.getTag()),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}
