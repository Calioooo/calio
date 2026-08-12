package com.calio.calendar.aicalendar.service;

import com.calio.calendar.aicalendar.controller.dto.SendCalendarConversationMessageResponse;
import com.calio.calendar.aicalendar.service.dto.CalendarConversationTurn;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.common.time.IanaTimeZones;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

@Service
public class CalendarConversationService {

    private static final int MAX_MESSAGE_CODE_POINTS = 2_000;

    private final CalendarConversationPersistenceService persistenceService;
    private final CalendarAssistantAgent assistantAgent;

    public CalendarConversationService(
            CalendarConversationPersistenceService persistenceService,
            CalendarAssistantAgent assistantAgent
    ) {
        this.persistenceService = persistenceService;
        this.assistantAgent = assistantAgent;
    }

    public String createConversation(Long accountId) {
        return persistenceService.createConversation(accountId);
    }

    public SendCalendarConversationMessageResponse sendMessage(
            Long accountId,
            String conversationId,
            String message,
            String timeZone
    ) {
        ZoneId zoneId = validateMessage(message, timeZone);
        CalendarConversationTurn turn = persistenceService.recordUserMessage(
                accountId,
                conversationId,
                message
        );
        String assistantMessage = assistantAgent.answer(
                accountId,
                turn.conversationId(),
                zoneId,
                turn.history()
        );
        persistenceService.recordAssistantMessage(accountId, turn.conversationId(), assistantMessage);
        return new SendCalendarConversationMessageResponse(turn.conversationId(), assistantMessage);
    }

    private ZoneId validateMessage(String message, String timeZone) {
        if (message == null || message.isBlank() || message.codePointCount(0, message.length()) > MAX_MESSAGE_CODE_POINTS) {
            throw new CalioException(ErrorCode.VALIDATION_FAILED);
        }
        if (timeZone == null || !IanaTimeZones.contains(timeZone)) {
            throw new CalioException(ErrorCode.INVALID_TIME_ZONE);
        }
        return ZoneId.of(timeZone);
    }
}
