package com.calio.calendar.aicalendar.service;

import com.calio.calendar.aicalendar.controller.dto.SendCalendarConversationMessageResponse;
import com.calio.calendar.aicalendar.domain.CalendarConversation;
import com.calio.calendar.aicalendar.domain.CalendarConversationMessageRole;
import com.calio.calendar.aicalendar.service.dto.CalendarConversationTurn;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.common.time.IanaTimeZones;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CalendarConversationService {

    private static final int MAX_MESSAGE_CODE_POINTS = 2_000;
    private static final int MAX_HISTORY_SIZE = 20;

    private final CalendarConversationQueryService conversationQueryService;
    private final CalendarConversationCommandService conversationCommandService;
    private final CalendarAssistantAgent assistantAgent;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public CalendarConversationService(
            CalendarConversationQueryService conversationQueryService,
            CalendarConversationCommandService conversationCommandService,
            CalendarAssistantAgent assistantAgent,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.conversationQueryService = conversationQueryService;
        this.conversationCommandService = conversationCommandService;
        this.assistantAgent = assistantAgent;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public String createConversation(Long accountId) {
        return transactionTemplate.execute(status -> {
            CalendarConversation conversation = conversationCommandService.createConversation(
                    accountId,
                    clock.instant()
            );
            return conversation.getConversationId();
        });
    }

    public SendCalendarConversationMessageResponse sendMessage(
            Long accountId,
            String conversationId,
            String message,
            String timeZone
    ) {
        ZoneId zoneId = validateMessage(message, timeZone);
        CalendarConversationTurn turn = recordUserMessage(
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
        recordAssistantMessage(accountId, turn.conversationId(), assistantMessage);
        return new SendCalendarConversationMessageResponse(turn.conversationId(), assistantMessage);
    }

    public CalendarConversationTurn recordUserMessage(
            Long accountId,
            String conversationId,
            String message
    ) {
        return transactionTemplate.execute(status -> {
            CalendarConversation conversation = conversationQueryService.getConversation(accountId, conversationId);
            conversationCommandService.recordMessage(
                    conversation,
                    CalendarConversationMessageRole.USER,
                    message,
                    clock.instant()
            );
            return new CalendarConversationTurn(
                    conversation.getConversationId(),
                    conversationQueryService.getRecentHistory(conversation.getId(), MAX_HISTORY_SIZE)
            );
        });
    }

    public void recordAssistantMessage(Long accountId, String conversationId, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            CalendarConversation conversation = conversationQueryService.getConversation(accountId, conversationId);
            conversationCommandService.recordMessage(
                    conversation,
                    CalendarConversationMessageRole.ASSISTANT,
                    message,
                    clock.instant()
            );
        });
    }

    public int deleteInactiveConversations(Instant cutoff) {
        return transactionTemplate.execute(status -> conversationCommandService.deleteInactiveConversations(cutoff));
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
