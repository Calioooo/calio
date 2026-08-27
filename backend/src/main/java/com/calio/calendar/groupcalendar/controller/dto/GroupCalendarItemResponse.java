package com.calio.calendar.groupcalendar.controller.dto;

import com.calio.calendar.groupcalendar.event.domain.GroupCalendarEvent;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceEvent;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceOverride;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.tag.controller.dto.TagResponse;
import java.time.Instant;

public record GroupCalendarItemResponse(
        Long id,
        String title,
        String description,
        Instant startAt,
        Instant endAt,
        boolean allDay,
        String timeZone,
        Long recurrenceId,
        boolean isRecurrenceOccurrence,
        Instant originStartAt,
        TagResponse tag,
        String creatorNickname,
        String publicItemId
) {
    public static GroupCalendarItemResponse from(GroupCalendarEvent event, String creatorNickname) {
        return new GroupCalendarItemResponse(
                event.getId(), event.getTitle(), event.getDescription(), event.getStartAt(), event.getEndAt(),
                event.isAllDay(), event.getTimeZone(), null, false, null, TagResponse.from(event.getTag()),
                creatorNickname, "group-event:" + event.getId()
        );
    }

    public static GroupCalendarItemResponse recurrenceOccurrence(
            GroupCalendarRecurrenceEvent recurrenceEvent,
            RecurrenceOccurrence occurrence,
            String creatorNickname
    ) {
        return new GroupCalendarItemResponse(
                null, recurrenceEvent.getTitle(), recurrenceEvent.getDescription(), occurrence.startAt(),
                occurrence.endAt(), recurrenceEvent.isAllDay(), recurrenceEvent.getTimeZone(),
                recurrenceEvent.getId(), true, occurrence.originStartAt(), TagResponse.from(recurrenceEvent.getTag()),
                creatorNickname,
                "group-recurrence:" + recurrenceEvent.getId() + ":" + occurrence.originStartAt()
        );
    }

    public static GroupCalendarItemResponse recurrenceOverride(
            GroupCalendarRecurrenceOverride override,
            String creatorNickname
    ) {
        GroupCalendarRecurrenceEvent recurrenceEvent = override.getRecurrenceEvent();
        return new GroupCalendarItemResponse(
                null, override.getTitle(), override.getDescription(), override.getStartAt(), override.getEndAt(),
                override.isAllDay(), override.getTimeZone(), recurrenceEvent.getId(), true,
                override.getOriginStartAt(), TagResponse.from(recurrenceEvent.getTag()), creatorNickname,
                "group-recurrence:" + recurrenceEvent.getId() + ":" + override.getOriginStartAt()
        );
    }
}
