package com.calio.calendar.aicalendar.service.tool.dto;

public record CalendarLookupToolRequest(
        String startDate,
        String endDate,
        boolean includeDescriptions
) {
}
