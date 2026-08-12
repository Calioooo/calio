package com.calio.calendar.aicalendar.service.tool.dto;

public record CalendarToolEvent(
        String title,
        String start,
        String end,
        boolean allDay,
        String description
) {
}
