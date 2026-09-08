package com.calio.calendar.integration.sync.operation.dto;

import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import java.time.Instant;
import java.util.List;

public record GoogleRecurrenceMasterJobPayload(
        String title,
        String description,
        Instant startAt,
        Instant endAt,
        boolean allDay,
        String timeZone,
        List<String> recurrence
) {
    public GoogleRecurrenceMasterJobPayload {
        recurrence = List.copyOf(recurrence);
    }

    public static GoogleRecurrenceMasterJobPayload from(RecurrenceEvent recurrenceEvent) {
        return new GoogleRecurrenceMasterJobPayload(
                recurrenceEvent.getTitle(), recurrenceEvent.getDescription(),
                recurrenceEvent.getFirstOccurrenceStartAt(), recurrenceEvent.getFirstOccurrenceEndAt(),
                recurrenceEvent.isAllDay(), recurrenceEvent.getTimeZone(),
                recurrenceEvent.getRecurrenceRules());
    }
}
