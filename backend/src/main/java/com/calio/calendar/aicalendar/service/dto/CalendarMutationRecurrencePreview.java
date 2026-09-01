package com.calio.calendar.aicalendar.service.dto;

import java.util.List;

public record CalendarMutationRecurrencePreview(
        List<String> before,
        List<String> after
) {

    public CalendarMutationRecurrencePreview {
        before = List.copyOf(before);
        after = List.copyOf(after);
    }
}
