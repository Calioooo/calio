package com.calio.calendar.recurrence.service;

import com.calio.calendar.recurrence.domain.PersonalRecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.dto.RecurrenceOverrideView;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PersonalRecurrenceOccurrenceResolver {

  private final Rfc5545RecurrenceEngine recurrenceEngine;

  public PersonalRecurrenceOccurrenceResolver(Rfc5545RecurrenceEngine recurrenceEngine) {
    this.recurrenceEngine = recurrenceEngine;
  }

  public List<RecurrenceOccurrence> expand(
      RecurrenceEvent recurrenceEvent, Instant from, Instant to) {
    return recurrenceEngine.expand(
        RecurrenceSchedule.from(recurrenceEvent), recurrenceEvent.getRecurrenceRules(), from, to);
  }

  public List<PersonalRecurrenceOccurrence> resolve(
      RecurrenceEvent recurrenceEvent,
      List<RecurrenceOccurrence> occurrences,
      List<RecurrenceEventOverride> overrides,
      Instant from,
      Instant to) {
    Map<Instant, RecurrenceEventOverride> overridesByOrigin =
        overrides.stream()
            .collect(
                Collectors.toMap(RecurrenceEventOverride::getOriginStartAt, Function.identity()));
    return occurrences.stream()
        .map(
            occurrence ->
                resolve(
                    recurrenceEvent, occurrence, overridesByOrigin.get(occurrence.originStartAt())))
        .filter(java.util.Objects::nonNull)
        .filter(occurrence -> occurrence.overlaps(from, to))
        .toList();
  }

  public List<PersonalRecurrenceOccurrence> resolveMovedIn(
      List<RecurrenceOverrideView> overrides, Instant from, Instant to) {
    return overrides.stream()
        .map(
            view ->
                PersonalRecurrenceOccurrence.overridden(view.recurrenceEvent(), view.override()))
        .filter(occurrence -> occurrence.overlaps(from, to))
        .toList();
  }

  private PersonalRecurrenceOccurrence resolve(
      RecurrenceEvent recurrenceEvent,
      RecurrenceOccurrence occurrence,
      RecurrenceEventOverride override) {
    if (override == null) {
      return PersonalRecurrenceOccurrence.generated(recurrenceEvent, occurrence);
    }
    if (override.isDeleted()) {
      return null;
    }
    return PersonalRecurrenceOccurrence.overridden(recurrenceEvent, override);
  }
}
