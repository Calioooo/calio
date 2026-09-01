package com.calio.calendar.aicalendar.service.tool.dto;

public record FreeTimeSearchToolRequest(
        String startDate,
        String endDate,
        String windowStart,
        String windowEnd,
        int minimumDurationMinutes
) {
}
