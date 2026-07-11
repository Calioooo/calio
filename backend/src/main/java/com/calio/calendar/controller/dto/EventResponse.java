package com.calio.calendar.controller.dto;

import com.calio.calendar.repository.entity.Event;
import com.calio.calendar.repository.entity.RecurrenceEvent;
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
        Instant originStartAt,
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
                null,
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }

    public static EventResponse recurrenceOccurrence(
            RecurrenceEvent recurrenceEvent,
            Instant originStartAt,
            Instant startAt,
            Instant endAt
    ) {
        return new EventResponse(
                null,
                recurrenceEvent.getRecurrenceTitle(),
                recurrenceEvent.getRecurrenceDescription(),
                startAt,
                endAt,
                false,
                recurrenceEvent.getId(),
                true,
                TagResponse.from(recurrenceEvent.getTag()),
                originStartAt,
                recurrenceEvent.getCreatedAt(),
                recurrenceEvent.getUpdatedAt()
        );
    }
}
