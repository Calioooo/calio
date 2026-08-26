package com.calio.calendar.aicalendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.aicalendar.config.CalendarAIProperties;
import com.calio.calendar.aicalendar.domain.CalendarConversationMessageRole;
import com.calio.calendar.aicalendar.service.dto.CalendarAssistantRequest;
import com.calio.calendar.aicalendar.service.dto.CalendarConversationHistoryMessage;
import com.calio.calendar.aicalendar.service.tool.CalendarAgentTools;
import com.calio.calendar.event.service.EventService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;

class SpringAiCalendarAssistantAgentTest {

    @Test
    @DisplayName("assistant 응답에 내부 response block marker가 있으면 거부한다")
    void givenInternalResponseBlockMarker_whenValidateAssistantMessage_thenRejectsResponse() {
        assertThatThrownBy(() -> SpringAiCalendarAssistantAgent.requireSafeAssistantMessage(
                "ASSISTANT_RESPONSE_BLOCKS: internal data"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("AI calendar provider returned internal response data.");
    }

    @Test
    @DisplayName("assistant 응답에 내부 calendar response history가 있으면 거부한다")
    void givenCalendarResponseState_whenValidateAssistantMessage_thenRejectsResponse() {
        assertThatThrownBy(() -> SpringAiCalendarAssistantAgent.requireSafeAssistantMessage(
                "<calendar_response_history>{\"blocks\":[]}</calendar_response_history>"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("AI calendar provider returned internal response data.");
    }

    @Test
    @DisplayName("assistant 응답에 이전 내부 response state가 있으면 거부한다")
    void givenLegacyCalendarResponseState_whenValidateAssistantMessage_thenRejectsResponse() {
        assertThatThrownBy(() -> SpringAiCalendarAssistantAgent.requireSafeAssistantMessage(
                "<calio_response_state>{\"blocks\":[]}</calio_response_state>"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("AI calendar provider returned internal response data.");
    }

    @Test
    @DisplayName("assistant 응답이 내부 tool 요청 JSON으로 시작하면 거부한다")
    void givenInternalToolRequestJson_whenValidateAssistantMessage_thenRejectsResponse() {
        assertThatThrownBy(() -> SpringAiCalendarAssistantAgent.requireSafeAssistantMessage(
                "{\"request\":{\"operation\":\"CREATE_EVENT\"}}"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("AI calendar provider returned internal response data.");
    }

    @Test
    @DisplayName("일반 assistant 응답은 그대로 사용한다")
    void givenNaturalLanguageAssistantMessage_whenValidateAssistantMessage_thenReturnsResponse() {
        assertThat(SpringAiCalendarAssistantAgent.requireSafeAssistantMessage("내일 일정은 없습니다."))
                .isEqualTo("내일 일정은 없습니다.");
    }

    @Test
    @DisplayName("대화 이력은 역할을 보존하고 response block은 내부 state로 전달한다")
    @SuppressWarnings("unchecked")
    void givenConversationHistory_whenAnswer_thenPreservesMessageRoles() {
        // given
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("알겠습니다."))
        )));
        ObjectProvider<ChatModel> chatModelProvider = mock(ObjectProvider.class);
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        CalendarAIProperties properties = new CalendarAIProperties();
        CalendarAgentTools tools = new CalendarAgentTools(
                mock(EventService.class),
                properties,
                mock(CalendarAgentObservationService.class)
        );
        SpringAiCalendarAssistantAgent agent = new SpringAiCalendarAssistantAgent(
                chatModelProvider,
                tools,
                properties,
                mock(CalendarAgentObservationService.class),
                Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"), ZoneId.of("UTC")),
                new ClassPathResource("prompts/calendar-assistant-system.st"),
                "test-model"
        );

        // when
        try {
            agent.answer(new CalendarAssistantRequest(
                    1L,
                    "conversation-1",
                    ZoneId.of("Asia/Seoul"),
                    List.of(
                            new CalendarConversationHistoryMessage(
                                    CalendarConversationMessageRole.USER,
                                    "내일 팀 회의 만들어줘"
                            ),
                            new CalendarConversationHistoryMessage(
                                    CalendarConversationMessageRole.ASSISTANT,
                                    "오후 2시부터 3시까지 팀 회의가 있습니다.",
                                    "[{\"type\":\"EVENTS\",\"items\":[]}]"
                            ),
                            new CalendarConversationHistoryMessage(
                                    CalendarConversationMessageRole.USER,
                                    "적용해줘"
                            )
                    )
            ));
        } finally {
            agent.close();
        }

        // then
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        List<Message> instructions = promptCaptor.getValue().getInstructions();
        List<Message> conversationMessages = instructions.stream()
                .filter(message -> message.getMessageType() != MessageType.SYSTEM)
                .toList();
        assertThat(conversationMessages).extracting(Message::getMessageType)
                .containsExactly(MessageType.USER, MessageType.ASSISTANT, MessageType.USER);
        assertThat(((AbstractMessage) conversationMessages.get(0)).getText())
                .isEqualTo("내일 팀 회의 만들어줘");
        assertThat(((AbstractMessage) conversationMessages.get(1)).getText())
                .isEqualTo("오후 2시부터 3시까지 팀 회의가 있습니다.");
        assertThat(((AbstractMessage) conversationMessages.get(2)).getText())
                .isEqualTo("적용해줘");
        assertThat(instructions.stream()
                .filter(message -> message.getMessageType() == MessageType.SYSTEM)
                .map(message -> ((AbstractMessage) message).getText())
        ).anySatisfy(message -> assertThat(message)
                .contains("<calendar_response_history>")
                .contains("EVENTS"));
    }
}
