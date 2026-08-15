package com.calio.calendar.aicalendar.service.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.calio.calendar.aicalendar.domain.CalendarConversationMessageRole;
import com.calio.calendar.aicalendar.service.CalendarAssistantAgent;
import com.calio.calendar.aicalendar.service.dto.CalendarAssistantAnswer;
import com.calio.calendar.aicalendar.service.dto.CalendarAssistantRequest;
import com.calio.calendar.aicalendar.service.dto.CalendarConversationHistoryMessage;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.service.EventService;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-calendar-eval;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.ai.model.chat=openai",
        "spring.ai.openai.chat.temperature=0"
})
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class CalendarAssistantEvalTest {

    @Autowired
    private CalendarAssistantAgent assistantAgent;

    @Autowired
    private ChatModel chatModel;

    @MockitoBean
    private EventService eventService;

    @Test
    @DisplayName("일정 조회 요청은 가상 tool 결과에 근거한 자연어와 EVENTS 블록을 반환한다")
    void givenAgendaRequest_whenEvaluate_thenClassifiesUsesToolAndReturnsGroundedAnswer() {
        // given
        CalendarAssistantRequest request = new CalendarAssistantRequest(
                1L,
                "eval-conversation",
                ZoneId.of("Asia/Seoul"),
                List.of(new CalendarConversationHistoryMessage(
                        CalendarConversationMessageRole.USER,
                        "2026년 8월 15일 일정 알려줘"
                ))
        );
        EventResponse event = event();
        when(eventService.listEvents(any(), any(), any())).thenReturn(List.of(event));

        // when
        CalendarAssistantAnswer answer = assistantAgent.answer(request);
        EvaluationResponse factCheck = factCheckingEvaluator().evaluate(new EvaluationRequest(
                "2026년 8월 15일 일정 알려줘",
                List.of(new Document("""
                        2026-08-15 calendar event:
                        title: 팀 회의
                        start: 2026-08-15T05:00:00Z
                        end: 2026-08-15T06:00:00Z
                        all-day: false
                        """)),
                answer.message()
        ));

        // then
        verify(eventService).listEvents(any(), any(), any());
        assertThat(answer.events()).containsExactly(event);
        assertThat(answer.freeTimes()).isEmpty();
        assertThat(factCheck.isPass()).withFailMessage(factCheck.getFeedback()).isTrue();
    }

    @Test
    @DisplayName("일정 변경 요청은 calendar tool과 blocks 없이 지원 범위를 안내한다")
    void givenCalendarWriteRequest_whenEvaluate_thenReturnsAnswerWithoutToolResults() {
        // when
        CalendarAssistantAnswer answer = assistantAgent.answer(request("내일 오후 2시에 회의 만들어줘"));

        // then
        verifyNoInteractions(eventService);
        assertThat(answer.events()).isEmpty();
        assertThat(answer.freeTimes()).isEmpty();
    }

    @Test
    @DisplayName("무관한 요청은 calendar tool과 blocks 없이 지원 범위를 안내한다")
    void givenUnrelatedRequest_whenEvaluate_thenReturnsAnswerWithoutToolResults() {
        // when
        CalendarAssistantAnswer answer = assistantAgent.answer(request("오늘 점심 메뉴 추천해줘"));

        // then
        verifyNoInteractions(eventService);
        assertThat(answer.events()).isEmpty();
        assertThat(answer.freeTimes()).isEmpty();
    }

    private CalendarAssistantRequest request(String message) {
        return new CalendarAssistantRequest(
                1L,
                "eval-conversation",
                ZoneId.of("Asia/Seoul"),
                List.of(new CalendarConversationHistoryMessage(
                        CalendarConversationMessageRole.USER,
                        message
                ))
        );
    }

    private FactCheckingEvaluator factCheckingEvaluator() {
        return FactCheckingEvaluator.builder(ChatClient.builder(chatModel)).build();
    }

    private EventResponse event() {
        return new EventResponse(
                1L,
                "팀 회의",
                "주간 계획을 검토합니다.",
                Instant.parse("2026-08-15T05:00:00Z"),
                Instant.parse("2026-08-15T06:00:00Z"),
                false,
                "Asia/Seoul",
                false,
                null,
                false,
                null,
                null,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z")
        );
    }
}
