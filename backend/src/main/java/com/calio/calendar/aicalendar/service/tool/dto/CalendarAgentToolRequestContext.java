package com.calio.calendar.aicalendar.service.tool.dto;

import java.time.ZoneId;

public record CalendarAgentToolRequestContext(
        Long accountId,
        String conversationId,
        ZoneId timeZone
) {
}
