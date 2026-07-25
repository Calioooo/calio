package com.calio.calendar.external.google.dto;

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
