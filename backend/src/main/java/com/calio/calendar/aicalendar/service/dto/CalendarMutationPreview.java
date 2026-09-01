package com.calio.calendar.aicalendar.service.dto;

import com.calio.calendar.aicalendar.domain.CalendarMutationScope;
import com.calio.calendar.aicalendar.domain.CalendarMutationType;
import com.calio.calendar.event.controller.dto.EventResponse;

public record CalendarMutationPreview(
        CalendarMutationType type,
        CalendarMutationScope scope,
        EventResponse before,
        EventResponse after,
        CalendarMutationRecurrencePreview recurrence
) {

    public CalendarMutationPreview(
            CalendarMutationType type,
            CalendarMutationScope scope,
            EventResponse before,
            EventResponse after
    ) {
        this(type, scope, before, after, null);
    }
}
