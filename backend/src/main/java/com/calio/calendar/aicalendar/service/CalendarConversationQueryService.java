package com.calio.calendar.aicalendar.service;

import com.calio.calendar.aicalendar.domain.CalendarConversation;
import com.calio.calendar.aicalendar.domain.CalendarConversationMessage;
import com.calio.calendar.aicalendar.repository.CalendarConversationMessageRepository;
import com.calio.calendar.aicalendar.repository.CalendarConversationRepository;
import com.calio.calendar.aicalendar.service.dto.CalendarConversationHistoryMessage;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CalendarConversationQueryService {

    private final CalendarConversationRepository conversationRepository;
    private final CalendarConversationMessageRepository messageRepository;

    public CalendarConversationQueryService(
            CalendarConversationRepository conversationRepository,
            CalendarConversationMessageRepository messageRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    public CalendarConversation getConversation(Long accountId, String conversationId) {
        return conversationRepository.findByConversationIdAndAccount_Id(conversationId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.AI_CALENDAR_CONVERSATION_NOT_FOUND));
    }

    public List<CalendarConversationHistoryMessage> getRecentHistory(Long conversationId, int limit) {
        return messageRepository.findByConversation_IdOrderByCreatedAtDescIdDesc(
                        conversationId,
                        PageRequest.of(0, limit)
                )
                .stream()
                .sorted(Comparator.comparing(CalendarConversationMessage::getCreatedAt)
                        .thenComparing(CalendarConversationMessage::getId))
                .map(message -> new CalendarConversationHistoryMessage(
                        message.getRole(),
                        message.getText(),
                        message.getAssistantResponseBlocksJson()
                ))
                .toList();
    }
}
