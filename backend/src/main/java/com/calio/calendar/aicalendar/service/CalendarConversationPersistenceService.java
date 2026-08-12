package com.calio.calendar.aicalendar.service;

import com.calio.calendar.aicalendar.domain.CalendarConversation;
import com.calio.calendar.aicalendar.domain.CalendarConversationMessageRole;
import com.calio.calendar.aicalendar.service.dto.CalendarConversationTurn;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarConversationPersistenceService {

    private final CalendarConversationQueryService conversationQueryService;
    private final CalendarConversationCommandService conversationCommandService;
    private final Clock clock;

    public CalendarConversationPersistenceService(
            CalendarConversationQueryService conversationQueryService,
            CalendarConversationCommandService conversationCommandService,
            Clock clock
    ) {
        this.conversationQueryService = conversationQueryService;
        this.conversationCommandService = conversationCommandService;
        this.clock = clock;
    }

    @Transactional
    public String createConversation(Long accountId) {
        CalendarConversation conversation = conversationCommandService.createConversation(
                accountId,
                clock.instant()
        );
        return conversation.getConversationId();
    }

    @Transactional
    public CalendarConversationTurn recordUserMessage(
            Long accountId,
            String conversationId,
            String message
    ) {
        CalendarConversation conversation = conversationQueryService.getConversation(accountId, conversationId);
        conversationCommandService.recordMessage(
                conversation,
                CalendarConversationMessageRole.USER,
                message,
                clock.instant()
        );
        return new CalendarConversationTurn(
                conversation.getConversationId(),
                conversationQueryService.getRecentHistory(conversation.getId())
        );
    }

    @Transactional
    public void recordAssistantMessage(Long accountId, String conversationId, String message) {
        CalendarConversation conversation = conversationQueryService.getConversation(accountId, conversationId);
        conversationCommandService.recordMessage(
                conversation,
                CalendarConversationMessageRole.ASSISTANT,
                message,
                clock.instant()
        );
    }

    @Transactional
    public int deleteInactiveConversations(Instant cutoff) {
        return conversationCommandService.deleteInactiveConversations(cutoff);
    }
}
