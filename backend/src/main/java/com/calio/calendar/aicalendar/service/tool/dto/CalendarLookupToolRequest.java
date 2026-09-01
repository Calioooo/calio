package com.calio.calendar.aicalendar.service.tool.dto;

import org.springframework.ai.tool.annotation.ToolParam;

public record CalendarLookupToolRequest(
        @ToolParam(description = "Inclusive start date in ISO-8601 yyyy-MM-dd format.", required = true)
        String startDate,
        @ToolParam(description = "Inclusive end date in ISO-8601 yyyy-MM-dd format.", required = true)
        String endDate
) {
}
