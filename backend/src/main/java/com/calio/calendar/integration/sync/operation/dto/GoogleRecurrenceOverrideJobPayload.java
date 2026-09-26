package com.calio.calendar.integration.sync.operation.dto;

import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.singleevent.controller.dto.EventResponse;
import java.time.Instant;

public record GoogleRecurrenceOverrideJobPayload(
    String title,
    String description,
    Instant startAt,
    Instant endAt,
    boolean allDay,
    String timeZone) {
  public static GoogleRecurrenceOverrideJobPayload from(
      RecurrenceEventOverride recurrenceOverride) {
    return new GoogleRecurrenceOverrideJobPayload(
        recurrenceOverride.getOverrideTitle(), recurrenceOverride.getOverrideDescription(),
        recurrenceOverride.getOverrideStartAt(), recurrenceOverride.getOverrideEndAt(),
        recurrenceOverride.isOverrideAllDay(), recurrenceOverride.getOverrideTimeZone());
  }

  public static GoogleRecurrenceOverrideJobPayload from(EventResponse response) {
    return new GoogleRecurrenceOverrideJobPayload(
        response.title(),
        response.description(),
        response.startAt(),
        response.endAt(),
        response.allDay(),
        response.timeZone());
  }
}
