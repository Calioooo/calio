package com.calio.calendar.aicalendar.controller.dto;

public record SendCalendarConversationMessageResponse(
        String conversationId,
        String assistantMessage
) {
}
