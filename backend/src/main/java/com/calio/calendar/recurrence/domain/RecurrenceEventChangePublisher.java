package com.calio.calendar.recurrence.domain;

import java.time.Instant;

public interface RecurrenceEventChangePublisher {

  void recurrenceEventCreated(Long accountId, RecurrenceEvent recurrenceEvent);

  void recurrenceEventUpdated(Long accountId, RecurrenceEvent recurrenceEvent);

  void recurrenceEventDeleted(Long accountId, Long recurrenceEventId);

  void recurrenceOccurrenceUpdated(Long accountId, RecurrenceEventOverride recurrenceEventOverride);

  void recurrenceOccurrenceDeleted(Long accountId, Long recurrenceEventId, Instant originStartAt);
}
