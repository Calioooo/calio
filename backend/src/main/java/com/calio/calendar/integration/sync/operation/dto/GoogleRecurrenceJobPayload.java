package com.calio.calendar.integration.sync.operation.dto;

import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import java.time.Instant;
import java.util.List;

public record GoogleRecurrenceJobPayload(
    String title,
    String description,
    Instant startAt,
    Instant endAt,
    boolean allDay,
    String timeZone,
    List<String> recurrence) {
  public GoogleRecurrenceJobPayload {
    recurrence = List.copyOf(recurrence);
  }

  public static GoogleRecurrenceJobPayload from(RecurrenceEvent recurrenceEvent) {
    return new GoogleRecurrenceJobPayload(
        recurrenceEvent.getTitle(),
        recurrenceEvent.getDescription(),
        recurrenceEvent.getFirstOccurrenceStartAt(),
        recurrenceEvent.getFirstOccurrenceEndAt(),
        recurrenceEvent.isAllDay(),
        recurrenceEvent.getTimeZone(),
        recurrenceEvent.getRecurrenceRules());
  }

  public static GoogleRecurrenceJobPayload from(RecurrenceEventResponse response) {
    return new GoogleRecurrenceJobPayload(
        response.title(),
        response.description(),
        response.firstOccurrenceStartAt(),
        response.firstOccurrenceEndAt(),
        response.allDay(),
        response.timeZone(),
        response.recurrence());
  }

  public static GoogleRecurrenceJobPayload from(GoogleRecurrenceOverrideJobPayload payload) {
    return new GoogleRecurrenceJobPayload(
        payload.title(),
        payload.description(),
        payload.startAt(),
        payload.endAt(),
        payload.allDay(),
        payload.timeZone(),
        List.of());
  }

  public boolean hasRecurrence() {
    return !recurrence.isEmpty();
  }
}
