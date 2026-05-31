package com.calio.calendar.controller.dto;

import com.calio.calendar.repository.entity.Event;
import java.time.OffsetDateTime;

public record EventResponse(
        Long id,
        String title,
        String description,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getStartAt(),
                event.getEndAt(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}
