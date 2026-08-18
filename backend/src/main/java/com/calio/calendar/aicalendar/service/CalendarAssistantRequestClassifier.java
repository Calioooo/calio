package com.calio.calendar.aicalendar.service;

import com.calio.calendar.aicalendar.domain.CalendarAssistantRequestClassification;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class CalendarAssistantRequestClassifier {

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final Resource systemPrompt;

    public CalendarAssistantRequestClassifier(
            ObjectProvider<ChatModel> chatModelProvider,
            @Value("classpath:prompts/calendar-assistant-request-classifier-system.st") Resource systemPrompt
    ) {
        this.chatModelProvider = chatModelProvider;
        this.systemPrompt = systemPrompt;
    }

    public CalendarAssistantRequestClassification classify(String message) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw providerUnavailable(null);
        }
        try {
            CalendarAssistantRequestClassification classification = ChatClient.create(chatModel)
                    .prompt()
                    .system(system -> system.text(systemPrompt))
                    .user(message)
                    .call()
                    .entity(CalendarAssistantRequestClassification.class, spec ->
                            spec.useProviderStructuredOutput());
            if (classification == null) {
                throw new IllegalStateException("AI calendar request classifier returned an empty response.");
            }
            return classification;
        } catch (RuntimeException exception) {
            throw providerUnavailable(exception);
        }
    }

    private CalioException providerUnavailable(Throwable cause) {
        return new CalioException(ErrorCode.AI_CALENDAR_PROVIDER_UNAVAILABLE, cause);
    }
}
