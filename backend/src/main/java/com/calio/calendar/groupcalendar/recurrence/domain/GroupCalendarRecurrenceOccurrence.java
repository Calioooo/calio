package com.calio.calendar.groupcalendar.recurrence.domain;

import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import java.time.Instant;
import java.util.Objects;

/** A group-calendar recurrence occurrence after applying its override, if one exists. */
public record GroupCalendarRecurrenceOccurrence(
    GroupCalendarRecurrenceEvent recurrenceEvent,
    Instant originStartAt,
    String title,
    String description,
    Instant startAt,
    Instant endAt,
    boolean allDay,
    String timeZone) {

  public GroupCalendarRecurrenceOccurrence {
    Objects.requireNonNull(recurrenceEvent);
    Objects.requireNonNull(originStartAt);
    Objects.requireNonNull(title);
    Objects.requireNonNull(startAt);
    Objects.requireNonNull(endAt);
  }

  public static GroupCalendarRecurrenceOccurrence generated(
      GroupCalendarRecurrenceEvent recurrenceEvent, RecurrenceOccurrence occurrence) {
    return new GroupCalendarRecurrenceOccurrence(
        recurrenceEvent,
        occurrence.originStartAt(),
        recurrenceEvent.getTitle(),
        recurrenceEvent.getDescription(),
        occurrence.startAt(),
        occurrence.endAt(),
        recurrenceEvent.isAllDay(),
        recurrenceEvent.getTimeZone());
  }

  public static GroupCalendarRecurrenceOccurrence overridden(
      GroupCalendarRecurrenceOverride override) {
    GroupCalendarRecurrenceEvent recurrenceEvent = override.getRecurrenceEvent();
    return new GroupCalendarRecurrenceOccurrence(
        recurrenceEvent,
        override.getOriginStartAt(),
        override.getTitle(),
        override.getDescription(),
        override.getStartAt(),
        override.getEndAt(),
        override.isAllDay(),
        override.getTimeZone());
  }

  public boolean overlaps(Instant from, Instant to) {
    return startAt.isBefore(to) && endAt.isAfter(from);
  }
}
