package com.calio.calendar.recurrence.controller.dto;

import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.tag.controller.dto.TagResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record RecurrenceEventResponse(
        Long recurrenceId,
        String title,
        String description,
        boolean allDay,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        String timeZone,
        List<String> recurrence,
        TagResponse tag,
        Instant createdAt,
        Instant updatedAt
) {

    public static RecurrenceEventResponse from(RecurrenceEvent recurrenceEvent) {
        RecurrenceSchedule schedule = RecurrenceSchedule.from(recurrenceEvent);
        return new RecurrenceEventResponse(
                recurrenceEvent.getId(),
                recurrenceEvent.getRecurrenceTitle(),
                recurrenceEvent.getRecurrenceDescription(),
                recurrenceEvent.isAllDay(),
                schedule.startDate(),
                schedule.endDate(),
                schedule.startTime(),
                schedule.endTime(),
                recurrenceEvent.getTimeZone(),
                recurrenceEvent.getRecurrenceLines(),
                TagResponse.from(recurrenceEvent.getTag()),
                recurrenceEvent.getCreatedAt(),
                recurrenceEvent.getUpdatedAt()
        );
    }
}
