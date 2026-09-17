package com.calio.calendar.groupcalendar.recurrence.service;

import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceEvent;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceOverride;
import com.calio.calendar.groupcalendar.recurrence.domain.ResolvedGroupCalendarRecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Applies group-calendar recurrence and override rules without accessing persistence. */
@Component
public class GroupCalendarRecurrenceOccurrenceResolver {

  private final Rfc5545RecurrenceEngine recurrenceEngine;

  public GroupCalendarRecurrenceOccurrenceResolver(Rfc5545RecurrenceEngine recurrenceEngine) {
    this.recurrenceEngine = recurrenceEngine;
  }

  public List<RecurrenceOccurrence> expand(
      GroupCalendarRecurrenceEvent recurrenceEvent, Instant from, Instant to) {
    return recurrenceEngine.expand(
        recurrenceEvent.toRecurrenceSchedule(), recurrenceEvent.getRecurrenceRules(), from, to);
  }

  public List<ResolvedGroupCalendarRecurrenceOccurrence> resolve(
      GroupCalendarRecurrenceEvent recurrenceEvent,
      List<RecurrenceOccurrence> occurrences,
      List<GroupCalendarRecurrenceOverride> overrides,
      Instant from,
      Instant to) {
    Map<Instant, GroupCalendarRecurrenceOverride> overridesByOrigin =
        overrides.stream()
            .collect(
                Collectors.toMap(
                    GroupCalendarRecurrenceOverride::getOriginStartAt, Function.identity()));
    return occurrences.stream()
        .map(
            occurrence ->
                resolve(recurrenceEvent, occurrence, overridesByOrigin.get(occurrence.originStartAt())))
        .filter(java.util.Objects::nonNull)
        .filter(occurrence -> occurrence.overlaps(from, to))
        .toList();
  }

  public List<ResolvedGroupCalendarRecurrenceOccurrence> resolveMovedIn(
      List<GroupCalendarRecurrenceOverride> overrides, Instant from, Instant to) {
    return overrides.stream()
        .filter(override -> !override.isDeleted())
        .map(ResolvedGroupCalendarRecurrenceOccurrence::overridden)
        .filter(occurrence -> occurrence.overlaps(from, to))
        .toList();
  }

  private ResolvedGroupCalendarRecurrenceOccurrence resolve(
      GroupCalendarRecurrenceEvent recurrenceEvent,
      RecurrenceOccurrence occurrence,
      GroupCalendarRecurrenceOverride override) {
    if (override == null) {
      return ResolvedGroupCalendarRecurrenceOccurrence.generated(recurrenceEvent, occurrence);
    }
    if (override.isDeleted()) {
      return null;
    }
    return ResolvedGroupCalendarRecurrenceOccurrence.overridden(override);
  }
}
