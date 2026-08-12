package com.calio.calendar.aicalendar.service.dto;

import java.time.ZoneId;
import java.util.List;

public record CalendarAssistantRequest(
        Long accountId,
        String conversationId,
        ZoneId timeZone,
        List<CalendarConversationHistoryMessage> history
) {
}
