package com.calio.calendar.recurrence.service;

import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import java.time.Instant;
import java.util.List;

public interface Rfc5545RecurrenceEngine {

    List<String> validate(RecurrenceSchedule schedule, List<String> recurrenceLines);

    List<RecurrenceOccurrence> expand(
            RecurrenceSchedule schedule,
            List<String> recurrenceLines,
            Instant from,
            Instant to
    );

    boolean containsOrigin(
            RecurrenceSchedule schedule,
            List<String> recurrenceLines,
            Instant originStartAt
    );
}
