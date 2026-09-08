package com.calio.calendar.recurrence.domain;

import java.time.Instant;

public record RecurrenceOverrideIdentity(Long recurrenceId, Instant originStartAt) { }
