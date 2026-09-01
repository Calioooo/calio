package com.calio.calendar.recurrence.controller.dto;

import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.tag.controller.dto.TagResponse;
import java.time.Instant;
import java.util.List;

public record RecurrenceEventResponse(
        Long recurrenceId,
        String title,
        String description,
        boolean allDay,
        Instant firstOccurrenceStartAt,
        Instant firstOccurrenceEndAt,
        String timeZone,
        List<String> recurrence,
        TagResponse tag,
        Instant createdAt,
        Instant updatedAt,
        boolean canUpdateSeries
) {

    public static RecurrenceEventResponse from(RecurrenceEvent recurrenceEvent, boolean canUpdateSeries) {
        RecurrenceSchedule schedule = RecurrenceSchedule.from(recurrenceEvent);
        return new RecurrenceEventResponse(
                recurrenceEvent.getId(),
                recurrenceEvent.getTitle(),
                recurrenceEvent.getDescription(),
                recurrenceEvent.isAllDay(),
                schedule.firstOccurrenceStartAt(),
                schedule.firstOccurrenceEndAt(),
                recurrenceEvent.getTimeZone(),
                recurrenceEvent.getRecurrenceRules(),
                TagResponse.from(recurrenceEvent.getTag()),
                recurrenceEvent.getCreatedAt(),
                recurrenceEvent.getUpdatedAt(),
                canUpdateSeries
        );
    }
}
