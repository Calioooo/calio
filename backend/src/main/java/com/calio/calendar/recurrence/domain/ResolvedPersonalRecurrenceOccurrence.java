package com.calio.calendar.recurrence.domain;

import java.time.Instant;
import java.util.Objects;

/** A recurrence occurrence after applying its override, if one exists. */
public record ResolvedPersonalRecurrenceOccurrence(
    RecurrenceEvent recurrenceEvent,
    Instant originStartAt,
    String title,
    String description,
    Instant startAt,
    Instant endAt,
    boolean allDay,
    String timeZone) {

  public ResolvedPersonalRecurrenceOccurrence {
    Objects.requireNonNull(recurrenceEvent);
    Objects.requireNonNull(originStartAt);
    Objects.requireNonNull(title);
    Objects.requireNonNull(startAt);
    Objects.requireNonNull(endAt);
  }

  public static ResolvedPersonalRecurrenceOccurrence generated(
      RecurrenceEvent recurrenceEvent, RecurrenceOccurrence occurrence) {
    return new ResolvedPersonalRecurrenceOccurrence(
        recurrenceEvent,
        occurrence.originStartAt(),
        recurrenceEvent.getTitle(),
        recurrenceEvent.getDescription(),
        occurrence.startAt(),
        occurrence.endAt(),
        recurrenceEvent.isAllDay(),
        recurrenceEvent.isAllDay() ? null : recurrenceEvent.getTimeZone());
  }

  public static ResolvedPersonalRecurrenceOccurrence overridden(RecurrenceEventOverride override) {
    RecurrenceEvent recurrenceEvent = override.getRecurrenceEvent();
    return new ResolvedPersonalRecurrenceOccurrence(
        recurrenceEvent,
        override.getOriginStartAt(),
        override.getOverrideTitle(),
        override.getOverrideDescription(),
        override.getOverrideStartAt(),
        override.getOverrideEndAt(),
        override.isOverrideAllDay(),
        override.isOverrideAllDay() ? null : override.getOverrideTimeZone());
  }

  public boolean overlaps(Instant from, Instant to) {
    return startAt.isBefore(to) && endAt.isAfter(from);
  }
}
