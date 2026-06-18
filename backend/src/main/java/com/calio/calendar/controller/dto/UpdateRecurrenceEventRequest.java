package com.calio.calendar.controller.dto;

import com.calio.calendar.repository.entity.RecurrenceFrequency;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateRecurrenceEventRequest(
        String recurrenceTitle,
        String recurrenceDescription,
        LocalDate recurrenceStartDate,
        LocalDate recurrenceEndDate,
        LocalTime recurrenceStartTime,
        LocalTime recurrenceEndTime,
        RecurrenceFrequency recurrenceFrequency,
        Instant targetOccurrenceStartAt,
        Instant modifiedStartAt,
        Instant modifiedEndAt
) {
}
