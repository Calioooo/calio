package com.calio.calendar.groupcalendar.event.controller.dto;

import com.calio.calendar.groupcalendar.event.domain.GroupCalendarEvent;
import com.calio.calendar.tag.controller.dto.TagResponse;
import java.time.Instant;

public record GroupCalendarEventResponse(
        Long id,
        String title,
        String description,
        Instant startAt,
        Instant endAt,
        boolean allDay,
        String timeZone,
        TagResponse tag
) {
    public static GroupCalendarEventResponse from(GroupCalendarEvent event) {
        return new GroupCalendarEventResponse(event.getId(), event.getTitle(), event.getDescription(), event.getStartAt(), event.getEndAt(), event.isAllDay(), event.getTimeZone(), TagResponse.from(event.getTag()));
    }
}
