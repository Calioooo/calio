package com.calio.calendar.aicalendar.service.dto;

import java.util.List;

public record CalendarConversationTurn(
        String conversationId,
        List<CalendarConversationHistoryMessage> history
) {
}
