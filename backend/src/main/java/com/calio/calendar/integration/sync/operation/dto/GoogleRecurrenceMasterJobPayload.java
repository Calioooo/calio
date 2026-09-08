package com.calio.calendar.integration.sync.operation.dto;

import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
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

    public static GoogleRecurrenceMasterJobPayload from(RecurrenceEventResponse response) {
        return new GoogleRecurrenceMasterJobPayload(
                response.title(), response.description(), response.firstOccurrenceStartAt(),
                response.firstOccurrenceEndAt(), response.allDay(), response.timeZone(),
                response.recurrence());
    }
}
