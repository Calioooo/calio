package com.calio.calendar.recurrence.repository.dto;

import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;

public record RecurrenceOverrideView(
    RecurrenceEvent recurrenceEvent, RecurrenceEventOverride override) {}
