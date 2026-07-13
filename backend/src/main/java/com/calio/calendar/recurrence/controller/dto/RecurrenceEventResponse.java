package com.calio.calendar.recurrence.controller.dto;

import com.calio.calendar.tag.controller.dto.TagResponse;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceFrequency;
import java.time.LocalDate;
import java.time.LocalTime;

public record RecurrenceEventResponse(
        Long recurrenceId,
        String recurrenceTitle,
        String recurrenceDescription,
        LocalDate recurrenceStartDate,
        LocalDate recurrenceEndDate,
        LocalTime recurrenceStartTime,
        LocalTime recurrenceEndTime,
        RecurrenceFrequency recurrenceFrequency,
        TagResponse tag
) {

    public static RecurrenceEventResponse from(RecurrenceEvent recurrenceEvent) {
        return new RecurrenceEventResponse(
                recurrenceEvent.getId(),
                recurrenceEvent.getRecurrenceTitle(),
                recurrenceEvent.getRecurrenceDescription(),
                recurrenceEvent.getRecurrenceStartDate(),
                recurrenceEvent.getRecurrenceEndDate(),
                recurrenceEvent.getRecurrenceStartTime(),
                recurrenceEvent.getRecurrenceEndTime(),
                recurrenceEvent.getRecurrenceFrequency(),
                TagResponse.from(recurrenceEvent.getTag())
        );
    }
}
