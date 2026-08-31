package com.calio.calendar.aicalendar.service;

import com.calio.calendar.aicalendar.controller.dto.SendCalendarConversationMessageResponse;
import com.calio.calendar.aicalendar.domain.CalendarConversation;
import com.calio.calendar.aicalendar.domain.CalendarConversationMessageRole;
import com.calio.calendar.aicalendar.service.dto.CalendarAssistantRequest;
import com.calio.calendar.aicalendar.service.dto.CalendarAssistantAnswer;
import com.calio.calendar.aicalendar.service.dto.CalendarConversationHistoryMessage;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.common.time.IanaTimeZones;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CalendarConversationService {

    private static final int MAX_MESSAGE_LENGTH = 2_000;
    private static final int MAX_HISTORY_SIZE = 20;

    private final CalendarConversationQueryService conversationQueryService;
    private final CalendarConversationCommandService conversationCommandService;
    private final SpringAiCalendarAssistantAgent assistantAgent;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public CalendarConversationService(
            CalendarConversationQueryService conversationQueryService,
            CalendarConversationCommandService conversationCommandService,
            SpringAiCalendarAssistantAgent assistantAgent,
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
        validateMessage(message);
        ZoneId zoneId = parseTimeZone(timeZone);
        CalendarConversation conversation = conversationQueryService.getConversation(accountId, conversationId);
        List<CalendarConversationHistoryMessage> history = recordUserMessageAndGetRecentHistory(
                conversation.getId(),
                message
        );
        CalendarAssistantRequest request = new CalendarAssistantRequest(
                accountId,
                conversationId,
                zoneId,
                history
        );
        CalendarAssistantAnswer answer = assistantAgent.answer(request);
        recordAssistantMessage(conversation.getId(), answer.message());
        return SendCalendarConversationMessageResponse.from(conversationId, answer);
    }

    List<CalendarConversationHistoryMessage> recordUserMessageAndGetRecentHistory(
            Long conversationId,
            String message
    ) {
        return transactionTemplate.execute(status -> {
            recordUserMessage(conversationId, message);
            return getRecentHistory(conversationId);
        });
    }

    private void recordUserMessage(Long conversationId, String message) {
        conversationCommandService.recordMessage(
                conversationId,
                CalendarConversationMessageRole.USER,
                message,
                clock.instant()
        );
    }

    private List<CalendarConversationHistoryMessage> getRecentHistory(Long conversationId) {
        return conversationQueryService.getRecentHistory(conversationId, MAX_HISTORY_SIZE);
    }

    void recordAssistantMessage(Long conversationId, String message) {
        transactionTemplate.executeWithoutResult(status -> conversationCommandService.recordMessage(
                conversationId,
                CalendarConversationMessageRole.ASSISTANT,
                message,
                clock.instant()
        ));
    }

    public int deleteInactiveConversations(Instant cutoff) {
        return transactionTemplate.execute(status -> conversationCommandService.deleteInactiveConversations(cutoff));
    }

    private void validateMessage(String message) {
        if (message == null || message.isBlank() || message.codePointCount(0, message.length()) > MAX_MESSAGE_LENGTH) {
            throw new CalioException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private ZoneId parseTimeZone(String timeZone) {
        if (timeZone == null || !IanaTimeZones.contains(timeZone)) {
            throw new CalioException(ErrorCode.INVALID_TIME_ZONE);
        }
        return ZoneId.of(timeZone);
    }
}
