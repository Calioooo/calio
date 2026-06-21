package com.calio.calendar.controller.dto;

import com.calio.calendar.repository.entity.RecurrenceEvent;
import com.calio.calendar.repository.entity.RecurrenceFrequency;
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
        RecurrenceFrequency recurrenceFrequency
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
                recurrenceEvent.getRecurrenceFrequency()
        );
    }
}
