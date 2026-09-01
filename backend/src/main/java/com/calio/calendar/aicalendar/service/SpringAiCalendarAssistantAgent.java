package com.calio.calendar.aicalendar.service;

import com.calio.calendar.aicalendar.config.CalendarAIProperties;
import com.calio.calendar.aicalendar.domain.CalendarConversationMessageRole;
import com.calio.calendar.aicalendar.service.dto.CalendarAssistantRequest;
import com.calio.calendar.aicalendar.service.dto.CalendarAssistantAnswer;
import com.calio.calendar.aicalendar.service.dto.CalendarConversationHistoryMessage;
import com.calio.calendar.aicalendar.service.tool.CalendarAgentToolResultCollector;
import com.calio.calendar.aicalendar.service.tool.CalendarAgentTools;
import com.calio.calendar.aicalendar.service.tool.dto.CalendarAgentToolRequestContext;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

@Service
public class SpringAiCalendarAssistantAgent implements CalendarAssistantAgent, AutoCloseable {

    private static final int MAX_PROVIDER_REQUEST_ATTEMPTS = 2;
    private static final String ASSISTANT_RESPONSE_BLOCKS_MARKER = "ASSISTANT_RESPONSE_BLOCKS";
    private static final String LEGACY_CALIO_RESPONSE_STATE_TAG = "calio_response_state";
    private static final String CALENDAR_RESPONSE_HISTORY_TAG = "calendar_response_history";
    private static final String TOOL_REQUEST_JSON_PREFIX = "{\"request\":";

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final CalendarAgentTools calendarAgentTools;
    private final CalendarAIProperties properties;
    private final CalendarAgentObservationService observationService;
    private final Clock clock;
    private final Resource systemPrompt;
    private final String modelName;
    private final ExecutorService providerExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public SpringAiCalendarAssistantAgent(
            ObjectProvider<ChatModel> chatModelProvider,
            CalendarAgentTools calendarAgentTools,
            CalendarAIProperties properties,
            CalendarAgentObservationService observationService,
            Clock clock,
            @Value("classpath:prompts/calendar-assistant-system.st") Resource systemPrompt,
            @Value("${spring.ai.openai.chat.model:}") String modelName
    ) {
        this.chatModelProvider = chatModelProvider;
        this.calendarAgentTools = calendarAgentTools;
        this.properties = properties;
        this.observationService = observationService;
        this.clock = clock;
        this.systemPrompt = systemPrompt;
        this.modelName = modelName;
    }

    @Override
    public CalendarAssistantAnswer answer(CalendarAssistantRequest request) {
        Instant startedAt = clock.instant();
        try {
            CalendarAssistantAnswer answer = requestProviderAnswer(request);
            observationService.recordRun(
                    request.conversationId(),
                    modelName,
                    "SUCCESS",
                    Duration.between(startedAt, clock.instant()),
                    null
            );
            return answer;
        } catch (RuntimeException exception) {
            observationService.recordRun(
                    request.conversationId(),
                    modelName,
                    "FAILURE",
                    Duration.between(startedAt, clock.instant()),
                    ErrorCode.AI_CALENDAR_PROVIDER_UNAVAILABLE.name()
            );
            throw exception;
        }
    }

    private CalendarAssistantAnswer requestProviderAnswer(CalendarAssistantRequest request) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null || modelName.isBlank()) {
            throw new CalioException(ErrorCode.AI_CALENDAR_PROVIDER_UNAVAILABLE);
        }
        CalendarAgentToolResultCollector resultCollector = new CalendarAgentToolResultCollector();
        Map<String, Object> toolContext = calendarAgentTools.createToolContext(
                new CalendarAgentToolRequestContext(
                        request.accountId(),
                        request.conversationId(),
                        request.timeZone()
                ),
                resultCollector
        );
        String responseState = responseState(request.history());
        String answer = requestProviderWithRetry(
                chatModel,
                conversationMessages(request.history()),
                systemPromptParameters(request.timeZone(), responseState),
                toolContext
        );
        return new CalendarAssistantAnswer(
                answer,
                resultCollector.events(),
                resultCollector.freeTimes(),
                resultCollector.mutationPreviews()
        );
    }

    private String requestProviderWithRetry(
            ChatModel chatModel,
            List<Message> history,
            Map<String, Object> systemPromptParameters,
            Map<String, Object> toolContext
    ) {
        long runDeadlineNanos = System.nanoTime() + properties.getRunTimeout().toNanos();
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_PROVIDER_REQUEST_ATTEMPTS; attempt++) {
            try {
                return requestProviderOnce(
                        chatModel,
                        history,
                        systemPromptParameters,
                        toolContext,
                        remainingTimeout(runDeadlineNanos)
                );
            } catch (RuntimeException exception) {
                lastFailure = exception;
                if (!shouldRetryProviderRequest(attempt, exception, runDeadlineNanos)) {
                    break;
                }
            }
        }
        throw providerFailure(lastFailure);
    }

    private boolean shouldRetryProviderRequest(
            int attempt,
            RuntimeException exception,
            long runDeadlineNanos
    ) {
        return attempt < MAX_PROVIDER_REQUEST_ATTEMPTS
                && isTransient(exception)
                && System.nanoTime() < runDeadlineNanos;
    }

    private String requestProviderOnce(
            ChatModel chatModel,
            List<Message> history,
            Map<String, Object> systemPromptParameters,
            Map<String, Object> toolContext,
            Duration timeout
    ) {
        Future<String> response = providerExecutor.submit(() -> ChatClient.create(chatModel)
                .prompt()
                .system(system -> system
                        .text(systemPrompt)
                        .params(systemPromptParameters)
                )
                .messages(history)
                .tools(calendarAgentTools)
                .toolContext(toolContext)
                .call()
                .content());
        try {
            String answer = response.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return requireSafeAssistantMessage(answer);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw providerFailure(exception);
        } catch (TimeoutException exception) {
            response.cancel(true);
            throw providerFailure(exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw providerFailure(cause);
        }
    }

    private Duration remainingTimeout(long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            throw providerFailure(new TimeoutException());
        }
        return Duration.ofNanos(remainingNanos);
    }

    private Map<String, Object> systemPromptParameters(ZoneId timeZone, String responseState) {
        Instant now = clock.instant();
        LocalDate today = now.atZone(timeZone).toLocalDate();
        LocalDate thisWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate nextWeekStart = thisWeekStart.plusWeeks(1);
        LocalDate weekendStart = thisWeekStart.plusDays(5);
        return Map.ofEntries(
                Map.entry("timeZone", timeZone.getId()),
                Map.entry("currentInstant", now),
                Map.entry("today", today),
                Map.entry("tomorrow", today.plusDays(1)),
                Map.entry("thisWeekStart", thisWeekStart),
                Map.entry("thisWeekEnd", thisWeekStart.plusDays(6)),
                Map.entry("nextWeekStart", nextWeekStart),
                Map.entry("nextWeekEnd", nextWeekStart.plusDays(6)),
                Map.entry("weekendStart", weekendStart),
                Map.entry("weekendEnd", weekendStart.plusDays(1)),
                Map.entry("maximumQueryDays", properties.getMaximumQueryDays()),
                Map.entry("agendaDefaultEndDate", today.plusDays(properties.getMaximumQueryDays() - 1)),
                Map.entry("calendarResponseHistory", responseState)
        );
    }

    static String requireSafeAssistantMessage(String answer) {
        if (answer == null || answer.isBlank()) {
            throw new IllegalStateException("AI calendar provider returned an empty response.");
        }
        if (answer.contains(ASSISTANT_RESPONSE_BLOCKS_MARKER)
                || answer.contains("<" + LEGACY_CALIO_RESPONSE_STATE_TAG + ">")
                || answer.contains("<" + CALENDAR_RESPONSE_HISTORY_TAG + ">")
                || answer.stripLeading().startsWith(TOOL_REQUEST_JSON_PREFIX)) {
            throw new IllegalStateException("AI calendar provider returned internal response data.");
        }
        return answer;
    }

    private List<Message> conversationMessages(List<CalendarConversationHistoryMessage> history) {
        return history.stream()
                .map(this::conversationMessage)
                .toList();
    }

    private Message conversationMessage(CalendarConversationHistoryMessage message) {
        return switch (message.role()) {
            case USER -> new UserMessage(message.text());
            case ASSISTANT -> new AssistantMessage(message.text());
        };
    }

    private String responseState(List<CalendarConversationHistoryMessage> history) {
        String responseBlocks = history.stream()
                .filter(message -> message.role() == CalendarConversationMessageRole.ASSISTANT)
                .map(CalendarConversationHistoryMessage::assistantResponseBlocksJson)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
        if (responseBlocks.isBlank()) {
            return "";
        }
        return """
                <%s>
                %s
                </%s>
                """.formatted(CALENDAR_RESPONSE_HISTORY_TAG, responseBlocks, CALENDAR_RESPONSE_HISTORY_TAG);
    }

    private boolean isTransient(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ResourceAccessException || current instanceof IOException) {
                return true;
            }
            if (current instanceof RestClientResponseException responseException
                    && responseException.getStatusCode().is5xxServerError()) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private CalioException providerFailure(Throwable cause) {
        return new CalioException(ErrorCode.AI_CALENDAR_PROVIDER_UNAVAILABLE, cause);
    }

    @Override
    public void close() {
        providerExecutor.close();
    }
}
