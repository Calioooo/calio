package com.calio.calendar.service;

import com.calio.calendar.repository.entity.RecurrenceFrequency;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

record MergedRecurrenceUpdate(
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        RecurrenceFrequency recurrenceFrequency
) {

    Instant startAt() {
        return startDate.atTime(startTime).toInstant(ZoneOffset.UTC);
    }

    Instant endAt() {
        return endDate.atTime(endTime).toInstant(ZoneOffset.UTC);
    }
}
