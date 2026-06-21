package com.calio.calendar.controller.dto;

import com.calio.calendar.repository.entity.RecurrenceEvent;
import com.calio.calendar.repository.entity.RecurrenceFrequency;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

public record RecurrenceEventResponse(
        Long recurrenceId,
        String title,
        String description,
        Instant startAt,
        Instant endAt,
        RecurrenceFrequency recurrenceFrequency,
        Instant createdAt,
        Instant updatedAt,
        String recurrenceTitle,
        String recurrenceDescription,
        LocalDate recurrenceStartDate,
        LocalDate recurrenceEndDate,
        LocalTime recurrenceStartTime,
        LocalTime recurrenceEndTime
) {

    public static RecurrenceEventResponse from(RecurrenceEvent recurrenceEvent) {
        return new RecurrenceEventResponse(
                recurrenceEvent.getId(),
                recurrenceEvent.getRecurrenceTitle(),
                recurrenceEvent.getRecurrenceDescription(),
                toInstant(recurrenceEvent.getRecurrenceStartDate(), recurrenceEvent.getRecurrenceStartTime()),
                toInstant(recurrenceEvent.getRecurrenceEndDate(), recurrenceEvent.getRecurrenceEndTime()),
                recurrenceEvent.getRecurrenceFrequency(),
                recurrenceEvent.getCreatedAt(),
                recurrenceEvent.getUpdatedAt(),
                recurrenceEvent.getRecurrenceTitle(),
                recurrenceEvent.getRecurrenceDescription(),
                recurrenceEvent.getRecurrenceStartDate(),
                recurrenceEvent.getRecurrenceEndDate(),
                recurrenceEvent.getRecurrenceStartTime(),
                recurrenceEvent.getRecurrenceEndTime()
        );
    }

    private static Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).toInstant(ZoneOffset.UTC);
    }
}
