package com.calio.calendar.aicalendar.service;

import com.calio.calendar.aicalendar.service.dto.CalendarConversationHistoryMessage;
import java.time.ZoneId;
import java.util.List;

public interface CalendarAssistantAgent {

    String answer(
            Long accountId,
            String conversationId,
            ZoneId timeZone,
            List<CalendarConversationHistoryMessage> history
    );
}
