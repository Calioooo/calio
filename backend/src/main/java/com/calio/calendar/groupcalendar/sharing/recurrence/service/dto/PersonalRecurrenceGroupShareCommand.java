package com.calio.calendar.groupcalendar.sharing.recurrence.service.dto;

import java.time.Instant;
import java.util.List;

public record PersonalRecurrenceGroupShareCommand(
        Long recurrenceId,
        boolean selectionEnabled,
        List<Instant> originStartAts
) {
}
