package com.calio.calendar.recurrence.domain;

import java.time.Instant;
import java.util.Objects;

/** A recurrence occurrence after applying its override, if one exists. */
public record PersonalRecurrenceOccurrence(
    RecurrenceEvent recurrenceEvent,
    Instant originStartAt,
    String title,
    String description,
    Instant startAt,
    Instant endAt,
    boolean allDay,
    String timeZone) {

  public PersonalRecurrenceOccurrence {
    Objects.requireNonNull(recurrenceEvent);
    Objects.requireNonNull(originStartAt);
    Objects.requireNonNull(title);
    Objects.requireNonNull(startAt);
    Objects.requireNonNull(endAt);
  }

  public static PersonalRecurrenceOccurrence generated(
      RecurrenceEvent recurrenceEvent, RecurrenceOccurrence occurrence) {
    return new PersonalRecurrenceOccurrence(
        recurrenceEvent,
        occurrence.originStartAt(),
        recurrenceEvent.getTitle(),
        recurrenceEvent.getDescription(),
        occurrence.startAt(),
        occurrence.endAt(),
        recurrenceEvent.isAllDay(),
        recurrenceEvent.isAllDay() ? null : recurrenceEvent.getTimeZone());
  }

  public static PersonalRecurrenceOccurrence overridden(RecurrenceEventOverride override) {
    RecurrenceEvent recurrenceEvent = override.getRecurrenceEvent();
    return new PersonalRecurrenceOccurrence(
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
