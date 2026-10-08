package com.calio.calendar.recurrence.service.dto;

import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;

public record RecurrenceOverrideView(
    RecurrenceEvent recurrenceEvent, RecurrenceEventOverride override) {}
