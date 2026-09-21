package com.calio.calendar.integration.sync.operation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

@Embeddable
public record GoogleCalendarRecurrenceJobTarget(
    @Column(name = "recurrence_event_id", updatable = false) Long recurrenceEventId,
    @Column(name = "origin_start_at", updatable = false) Instant originStartAt) {

  public GoogleCalendarRecurrenceJobTarget {
    if (recurrenceEventId == null || recurrenceEventId <= 0) {
      throw new IllegalArgumentException("Google recurrence-event ID must be positive");
    }
  }

  public static GoogleCalendarRecurrenceJobTarget forKind(
      GoogleCalendarRecurrenceJobKind kind, Long recurrenceEventId, Instant originStartAt) {
    if (kind == null) {
      throw new IllegalArgumentException("Google recurrence job kind is required");
    }
    if (kind.isOverrideJob() && originStartAt == null) {
      throw new IllegalArgumentException("Google recurrence override job requires originStartAt");
    }
    if (!kind.isOverrideJob() && originStartAt != null) {
      throw new IllegalArgumentException("Google recurrence-event job cannot target an occurrence");
    }
    return new GoogleCalendarRecurrenceJobTarget(recurrenceEventId, originStartAt);
  }
}
