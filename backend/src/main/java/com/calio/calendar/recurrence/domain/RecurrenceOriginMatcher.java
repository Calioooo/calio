package com.calio.calendar.recurrence.domain;

import java.time.Instant;
import java.util.List;

public interface RecurrenceOriginMatcher {

  boolean containsOrigin(
      RecurrenceSchedule schedule, List<String> recurrenceRules, Instant originStartAt);
}
