package com.calio.calendar.aicalendar.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.aicalendar.domain.CalendarConversation;
import com.calio.calendar.aicalendar.domain.CalendarConversationMessage;
import com.calio.calendar.aicalendar.domain.CalendarConversationMessageRole;
import com.calio.calendar.aicalendar.repository.CalendarConversationMessageRepository;
import com.calio.calendar.aicalendar.repository.CalendarConversationRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CalendarConversationCommandService {

    private final AccountRepository accountRepository;
    private final CalendarConversationRepository conversationRepository;
    private final CalendarConversationMessageRepository messageRepository;

    public CalendarConversationCommandService(
            AccountRepository accountRepository,
            CalendarConversationRepository conversationRepository,
            CalendarConversationMessageRepository messageRepository
    ) {
        this.accountRepository = accountRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    public CalendarConversation createConversation(Long accountId, Instant createdAt) {
        Account account = accountRepository.getReferenceById(accountId);
        return conversationRepository.save(new CalendarConversation(account, createdAt));
    }

    public void recordMessage(
            CalendarConversation conversation,
            CalendarConversationMessageRole role,
            String message,
            Instant recordedAt
    ) {
        messageRepository.save(new CalendarConversationMessage(conversation, role, message));
        conversation.touch(recordedAt);
    }

    public int deleteInactiveConversations(Instant cutoff) {
        messageRepository.deleteByConversationInactiveBefore(cutoff);
        return conversationRepository.deleteInactiveBefore(cutoff);
    }
}
