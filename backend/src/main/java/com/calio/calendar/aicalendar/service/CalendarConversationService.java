package com.calio.calendar.aicalendar.service;

import com.calio.calendar.aicalendar.controller.dto.CalendarAssistantBlockResponse;
import com.calio.calendar.aicalendar.controller.dto.SendCalendarConversationMessageResponse;
import com.calio.calendar.aicalendar.domain.CalendarAssistantRequestClassification;
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
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class CalendarConversationService {

    private static final int MAX_MESSAGE_LENGTH = 2_000;
    private static final int MAX_HISTORY_SIZE = 20;
    private static final List<String> UNSUPPORTED_MESSAGES = List.of(
            "일정 조회와 빈 시간 찾기를 도와드릴 수 있어요.",
            "캘린더의 일정 확인이나 빈 시간 찾기를 요청해 주세요.",
            "일정 조회와 빈 시간 찾기 기능을 지원해요."
    );

    private final CalendarConversationQueryService conversationQueryService;
    private final CalendarConversationCommandService conversationCommandService;
    private final CalendarAssistantRequestClassifier requestClassifier;
    private final CalendarAssistantAgent assistantAgent;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public CalendarConversationService(
            CalendarConversationQueryService conversationQueryService,
            CalendarConversationCommandService conversationCommandService,
            CalendarAssistantRequestClassifier requestClassifier,
            CalendarAssistantAgent assistantAgent,
            ObjectMapper objectMapper,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.conversationQueryService = conversationQueryService;
        this.conversationCommandService = conversationCommandService;
        this.requestClassifier = requestClassifier;
        this.assistantAgent = assistantAgent;
        this.objectMapper = objectMapper;
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
        if (requestClassifier.classify(message) == CalendarAssistantRequestClassification.UNSUPPORTED) {
            return recordUnsupportedResponse(conversation.getId(), conversationId);
        }
        CalendarAssistantRequest request = new CalendarAssistantRequest(
                accountId,
                conversationId,
                zoneId,
                history
        );
        CalendarAssistantAnswer answer = assistantAgent.answer(request);
        return recordAssistantResponse(conversation.getId(), conversationId, answer);
    }

    private SendCalendarConversationMessageResponse recordUnsupportedResponse(
            Long conversationId,
            String externalConversationId
    ) {
        CalendarAssistantAnswer answer = new CalendarAssistantAnswer(
                selectUnsupportedMessage(),
                List.of(),
                List.of(),
                List.of()
        );
        return recordAssistantResponse(conversationId, externalConversationId, answer);
    }

    private SendCalendarConversationMessageResponse recordAssistantResponse(
            Long conversationId,
            String externalConversationId,
            CalendarAssistantAnswer answer
    ) {
        SendCalendarConversationMessageResponse response =
                SendCalendarConversationMessageResponse.from(externalConversationId, answer);
        recordAssistantMessage(conversationId, response.assistantMessage(), response.blocks());
        return response;
    }

    private String selectUnsupportedMessage() {
        return UNSUPPORTED_MESSAGES.get(ThreadLocalRandom.current().nextInt(UNSUPPORTED_MESSAGES.size()));
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
                null,
                clock.instant()
        );
    }

    private List<CalendarConversationHistoryMessage> getRecentHistory(Long conversationId) {
        return conversationQueryService.getRecentHistory(conversationId, MAX_HISTORY_SIZE);
    }

    void recordAssistantMessage(Long conversationId, String message) {
        recordAssistantMessage(conversationId, message, List.of());
    }

    private void recordAssistantMessage(
            Long conversationId,
            String message,
            List<CalendarAssistantBlockResponse<?>> responseBlocks
    ) {
        transactionTemplate.executeWithoutResult(status -> conversationCommandService.recordMessage(
                conversationId,
                CalendarConversationMessageRole.ASSISTANT,
                message,
                serializeResponseBlocks(responseBlocks),
                clock.instant()
        ));
    }

    private String serializeResponseBlocks(List<CalendarAssistantBlockResponse<?>> responseBlocks) {
        try {
            return objectMapper.writeValueAsString(responseBlocks);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Assistant response blocks cannot be stored.", exception);
        }
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
