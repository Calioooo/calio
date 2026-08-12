package com.calio.calendar.aicalendar.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.aicalendar.domain.CalendarConversation;
import com.calio.calendar.aicalendar.domain.CalendarConversationMessage;
import com.calio.calendar.aicalendar.domain.CalendarConversationMessageRole;
import com.calio.calendar.aicalendar.repository.CalendarConversationMessageRepository;
import com.calio.calendar.aicalendar.repository.CalendarConversationRepository;
import com.calio.calendar.aicalendar.service.dto.CalendarConversationHistoryMessage;
import com.calio.calendar.aicalendar.service.dto.CalendarConversationTurn;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarConversationPersistenceService {

    private final AccountRepository accountRepository;
    private final CalendarConversationRepository conversationRepository;
    private final CalendarConversationMessageRepository messageRepository;
    private final Clock clock;

    public CalendarConversationPersistenceService(
            AccountRepository accountRepository,
            CalendarConversationRepository conversationRepository,
            CalendarConversationMessageRepository messageRepository,
            Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.clock = clock;
    }

    @Transactional
    public String createConversation(Long accountId) {
        Account account = accountRepository.getReferenceById(accountId);
        CalendarConversation conversation = conversationRepository.save(
                new CalendarConversation(account, clock.instant())
        );
        return conversation.getConversationId();
    }

    @Transactional
    public CalendarConversationTurn recordUserMessage(
            Long accountId,
            String conversationId,
            String message
    ) {
        CalendarConversation conversation = getOwnedConversation(accountId, conversationId);
        messageRepository.save(new CalendarConversationMessage(
                conversation,
                CalendarConversationMessageRole.USER,
                message
        ));
        conversation.touch(clock.instant());
        return new CalendarConversationTurn(
                conversation.getConversationId(),
                recentHistory(conversation.getId())
        );
    }

    @Transactional
    public void recordAssistantMessage(Long accountId, String conversationId, String message) {
        CalendarConversation conversation = getOwnedConversation(accountId, conversationId);
        messageRepository.save(new CalendarConversationMessage(
                conversation,
                CalendarConversationMessageRole.ASSISTANT,
                message
        ));
        conversation.touch(clock.instant());
    }

    @Transactional
    public int deleteInactiveConversations(Instant cutoff) {
        messageRepository.deleteByConversationInactiveBefore(cutoff);
        return conversationRepository.deleteInactiveBefore(cutoff);
    }

    private CalendarConversation getOwnedConversation(Long accountId, String conversationId) {
        return conversationRepository.findByConversationIdAndAccount_Id(conversationId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.AI_CALENDAR_CONVERSATION_NOT_FOUND));
    }

    private List<CalendarConversationHistoryMessage> recentHistory(Long conversationId) {
        return messageRepository.findTop20ByConversation_IdOrderByIdDesc(conversationId)
                .stream()
                .sorted(Comparator.comparing(CalendarConversationMessage::getId))
                .map(message -> new CalendarConversationHistoryMessage(
                        message.getRole(),
                        message.getText()
                ))
                .toList();
    }
}
