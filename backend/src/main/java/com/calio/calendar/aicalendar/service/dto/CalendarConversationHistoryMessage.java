package com.calio.calendar.aicalendar.service.dto;

import com.calio.calendar.aicalendar.domain.CalendarConversationMessageRole;

public record CalendarConversationHistoryMessage(
        CalendarConversationMessageRole role,
        String text
) {
}
