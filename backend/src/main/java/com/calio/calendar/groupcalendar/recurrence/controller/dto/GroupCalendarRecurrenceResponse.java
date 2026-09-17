package com.calio.calendar.groupcalendar.recurrence.controller.dto;

import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceEvent;
import com.calio.calendar.tag.controller.dto.TagResponse;
import java.time.Instant;
import java.util.List;

public record GroupCalendarRecurrenceResponse(
        Long id,
        String title,
        String description,
        boolean allDay,
        Instant firstOccurrenceStartAt,
        Instant firstOccurrenceEndAt,
        String timeZone,
        List<String> recurrence,
        TagResponse tag
) {
    public static GroupCalendarRecurrenceResponse from(GroupCalendarRecurrenceEvent event) {
        return new GroupCalendarRecurrenceResponse(
                event.getId(), event.getTitle(), event.getDescription(), event.isAllDay(),
                event.getFirstOccurrenceStartAt(), event.getFirstOccurrenceEndAt(), event.getTimeZone(),
                event.getRecurrenceRules(), TagResponse.from(event.getTag())
        );
    }
}
