package com.calio.calendar.external.google.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleCalendarEventTime(
        String date,
        String dateTime,
        String timeZone
) {

    public boolean isAllDay() {
        return hasText(date) && !hasText(dateTime);
    }

    public boolean isTimed() {
        return hasText(dateTime) && !hasText(date);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
