package com.calio.calendar.event.controller.dto;

import com.calio.calendar.tag.controller.dto.TagResponse;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import java.time.Instant;

public record EventResponse(
        Long id,
        String title,
        String description,
        Instant startAt,
        Instant endAt,
        boolean allDay,
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
                event.isAllDay(),
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
            RecurrenceOccurrence occurrence
    ) {
        return new EventResponse(
                null,
                recurrenceEvent.getRecurrenceTitle(),
                recurrenceEvent.getRecurrenceDescription(),
                occurrence.startAt(),
                occurrence.endAt(),
                recurrenceEvent.isAllDay(),
                false,
                recurrenceEvent.getId(),
                true,
                TagResponse.from(recurrenceEvent.getTag()),
                occurrence.originStartAt(),
                recurrenceEvent.getCreatedAt(),
                recurrenceEvent.getUpdatedAt()
        );
    }

    public static EventResponse recurrenceOverride(RecurrenceEventOverride override) {
        RecurrenceEvent recurrenceEvent = override.getRecurrenceEvent();
        return new EventResponse(
                null,
                override.getOverrideTitle(),
                override.getOverrideDescription(),
                override.getOverrideStartAt(),
                override.getOverrideEndAt(),
                override.isOverrideAllDay(),
                false,
                recurrenceEvent.getId(),
                true,
                TagResponse.from(recurrenceEvent.getTag()),
                override.getOriginStartAt(),
                recurrenceEvent.getCreatedAt(),
                recurrenceEvent.getUpdatedAt()
        );
    }
}
