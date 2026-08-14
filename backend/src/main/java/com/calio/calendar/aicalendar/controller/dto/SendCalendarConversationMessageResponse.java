package com.calio.calendar.aicalendar.controller.dto;

import com.calio.calendar.aicalendar.service.dto.CalendarAssistantAnswer;
import java.util.ArrayList;
import java.util.List;

public record SendCalendarConversationMessageResponse(
        String conversationId,
        String assistantMessage,
        List<CalendarAssistantBlockResponse<?>> blocks
) {

    public static SendCalendarConversationMessageResponse from(
            String conversationId,
            CalendarAssistantAnswer answer
    ) {
        List<CalendarAssistantBlockResponse<?>> blocks = new ArrayList<>();
        if (!answer.events().isEmpty()) {
            blocks.add(CalendarAssistantBlockResponse.events(answer.events()));
        }
        if (!answer.freeTimes().isEmpty()) {
            blocks.add(CalendarAssistantBlockResponse.freeTimes(answer.freeTimes().stream()
                    .map(FreeTimeResponse::from)
                    .toList()));
        }
        return new SendCalendarConversationMessageResponse(conversationId, answer.message(), blocks);
    }
}
