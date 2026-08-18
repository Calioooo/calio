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
import com.calio.calendar.event.service.dto.CalendarFreeTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
@Import(CalendarAssistantEvalTest.FixedClockConfig.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class CalendarAssistantEvalTest {

    private static final Instant EVALUATION_NOW = Instant.parse("2026-08-15T00:00:00Z");

    @Autowired
    private CalendarAssistantAgent assistantAgent;

    @Autowired
    private ChatModel chatModel;

    @MockitoBean
    private EventService eventService;

    @Test
    @DisplayName("시간 지정 일정은 사용자 시간대로 변환하고 근거 없는 시간대를 추정하지 않는다")
    void givenTimedEvent_whenEvaluateAgenda_thenReturnsGroundedLocalTimeWithoutInferredDayPart() {
        // given
        CalendarAssistantRequest request = request("2026년 8월 15일 일정 알려줘", "Asia/Seoul");
        EventResponse event = timedEvent(
                "팀 회의",
                "주간 계획을 검토합니다.",
                "2026-08-15T05:00:00Z",
                "2026-08-15T06:00:00Z",
                "Asia/Seoul"
        );
        String expectedSchedule = """
                User timezone: Asia/Seoul.
                Timed event on 2026-08-15:
                title: 팀 회의
                local time: 14:00 through 15:00
                all-day: false
                details: 주간 계획을 검토합니다.
                The user did not request a day-part window. Do not infer or claim one.
                """;

        // when
        AgendaEvaluation evaluation = evaluateAgenda(request, List.of(event), expectedSchedule);

        // then
        assertAgendaAnswer(evaluation, request, expectedSchedule, List.of(event));
        verify(eventService).listEvents(any(), any(), any());
    }

    @Test
    @DisplayName("UTC 날짜가 달라도 사용자 시간대의 로컬 날짜와 시각으로 일정을 설명한다")
    void givenEventCrossingUserDateBoundary_whenEvaluateAgenda_thenUsesUserLocalDateAndTime() {
        // given
        CalendarAssistantRequest request = request("2026년 8월 14일 일정 알려줘", "America/Los_Angeles");
        EventResponse event = timedEvent(
                "고객 통화",
                "미국 서부 시간 기준 통화입니다.",
                "2026-08-15T06:30:00Z",
                "2026-08-15T07:00:00Z",
                "Asia/Seoul"
        );
        String expectedSchedule = """
                User timezone: America/Los_Angeles.
                Timed event on 2026-08-14:
                title: 고객 통화
                local time: 23:30 through 00:00
                all-day: false
                The event source timezone does not change the user's display timezone.
                """;

        // when
        AgendaEvaluation evaluation = evaluateAgenda(request, List.of(event), expectedSchedule);

        // then
        assertAgendaAnswer(evaluation, request, expectedSchedule, List.of(event));
        verify(eventService).listEvents(any(), any(), any());
    }

    @Test
    @DisplayName("종일 일정은 시각 없이 날짜와 종일 상태로 설명한다")
    void givenAllDayEvent_whenEvaluateAgenda_thenDescribesDateWithoutTime() {
        // given
        CalendarAssistantRequest request = request("2026년 8월 15일 일정 알려줘", "Asia/Seoul");
        EventResponse event = allDayEvent("광복절", "2026-08-15T00:00:00Z", "2026-08-16T00:00:00Z");
        String expectedSchedule = """
                User timezone: Asia/Seoul.
                All-day event on 2026-08-15:
                title: 광복절
                all-day: true
                Do not present a clock time for this event.
                """;

        // when
        AgendaEvaluation evaluation = evaluateAgenda(request, List.of(event), expectedSchedule);

        // then
        assertAgendaAnswer(evaluation, request, expectedSchedule, List.of(event));
        verify(eventService).listEvents(any(), any(), any());
    }

    @Test
    @DisplayName("일정이 없는 날짜는 조회 후 일정이 없다고 답변하고 EVENTS 블록을 비운다")
    void givenNoEvents_whenEvaluateAgenda_thenDoesNotInventEvents() {
        // given
        CalendarAssistantRequest request = request("2026년 8월 15일 일정 알려줘", "Asia/Seoul");
        String expectedSchedule = """
                User timezone: Asia/Seoul.
                There are no calendar events on 2026-08-15.
                Do not invent an event or claim that an event exists.
                """;

        // when
        AgendaEvaluation evaluation = evaluateAgenda(request, List.of(), expectedSchedule);

        // then
        assertAgendaAnswer(evaluation, request, expectedSchedule, List.of());
        verify(eventService).listEvents(any(), any(), any());
    }

    @Test
    @DisplayName("여러 일정은 EVENTS 블록과 자연어에 누락 없이 설명한다")
    void givenFourUpcomingEvents_whenEvaluateAgenda_thenDescribesAllEvents() {
        // given
        CalendarAssistantRequest request = request("2026년 8월 16일 일정 알려줘", "Asia/Seoul");
        List<EventResponse> events = List.of(
                timedEvent("아침 회의", null, "2026-08-16T00:00:00Z", "2026-08-16T01:00:00Z", "Asia/Seoul"),
                timedEvent("디자인 리뷰", null, "2026-08-16T02:00:00Z", "2026-08-16T03:00:00Z", "Asia/Seoul"),
                timedEvent("고객 통화", null, "2026-08-16T04:00:00Z", "2026-08-16T05:00:00Z", "Asia/Seoul"),
                timedEvent("마감 회의", null, "2026-08-16T06:00:00Z", "2026-08-16T07:00:00Z", "Asia/Seoul")
        );
        String expectedSchedule = """
                User timezone: Asia/Seoul.
                There are four upcoming timed events on 2026-08-16: 아침 회의, 디자인 리뷰, 고객 통화, 마감 회의.
                The EVENTS block contains all four events.
                In natural language, mention all four events. Do not omit 마감 회의.
                """;

        // when
        AgendaEvaluation evaluation = evaluateAgenda(request, events, expectedSchedule);

        // then
        assertAgendaAnswer(evaluation, request, expectedSchedule, events);
        verify(eventService).listEvents(any(), any(), any());
    }

    @Test
    @DisplayName("빈 시간 조회는 FREE_TIMES 블록과 같은 시간 범위를 자연어로 설명한다")
    void givenFreeTimeRequest_whenEvaluate_thenReturnsGroundedFreeTimes() {
        // given
        CalendarAssistantRequest request = request(
                "2026년 8월 15일 오후 1시부터 5시 사이에 한 시간 이상 비는 시간 알려줘",
                "Asia/Seoul"
        );
        List<CalendarFreeTime> freeTimes = List.of(new CalendarFreeTime(
                "2026-08-15T15:00:00+09:00",
                "2026-08-15T17:00:00+09:00",
                List.of()
        ));
        String expectedFreeTimes = """
                User timezone: Asia/Seoul.
                Available time on 2026-08-15: 15:00 through 17:00.
                Do not claim another available time.
                """;
        when(eventService.findAvailableTimes(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(freeTimes);

        // when
        CalendarAssistantAnswer answer = assistantAgent.answer(request);
        EvaluationResponse factCheck = evaluateFacts(request.history().getFirst().text(), expectedFreeTimes, answer.message());

        // then
        assertThat(answer.events()).isEmpty();
        assertThat(answer.freeTimes())
                .withFailMessage(() -> evaluationFailureDetails(
                        request.history().getFirst().text(),
                        expectedFreeTimes,
                        answer.message(),
                        factCheck.getFeedback()
                ))
                .containsExactlyElementsOf(freeTimes);
        assertThat(factCheck.isPass())
                .withFailMessage(() -> evaluationFailureDetails(
                        request.history().getFirst().text(),
                        expectedFreeTimes,
                        answer.message(),
                        factCheck.getFeedback()
                ))
                .isTrue();
        verify(eventService).findAvailableTimes(any(), any(), any(), any(), any(), any(), any());
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
        return request(message, "Asia/Seoul");
    }

    private CalendarAssistantRequest request(String message, String timeZone) {
        return new CalendarAssistantRequest(
                1L,
                "eval-conversation",
                ZoneId.of(timeZone),
                List.of(new CalendarConversationHistoryMessage(
                        CalendarConversationMessageRole.USER,
                        message
                ))
        );
    }

    private FactCheckingEvaluator factCheckingEvaluator() {
        return FactCheckingEvaluator.builder(ChatClient.builder(chatModel)).build();
    }

    private AgendaEvaluation evaluateAgenda(
            CalendarAssistantRequest request,
            List<EventResponse> events,
            String expectedSchedule
    ) {
        when(eventService.listEvents(any(), any(), any())).thenReturn(events);
        CalendarAssistantAnswer answer = assistantAgent.answer(request);
        return new AgendaEvaluation(
                answer,
                evaluateFacts(request.history().getFirst().text(), expectedSchedule, answer.message())
        );
    }

    private EvaluationResponse evaluateFacts(String question, String reference, String assistantAnswer) {
        return factCheckingEvaluator().evaluate(new EvaluationRequest(
                question,
                List.of(new Document(reference)),
                assistantAnswer
        ));
    }

    private void assertAgendaAnswer(
            AgendaEvaluation evaluation,
            CalendarAssistantRequest request,
            String expectedSchedule,
            List<EventResponse> expectedEvents
    ) {
        assertThat(evaluation.answer().events())
                .withFailMessage(() -> evaluationFailureDetails(
                        request.history().getFirst().text(),
                        expectedSchedule,
                        evaluation.answer().message(),
                        evaluation.factCheck().getFeedback()
                ))
                .containsExactlyElementsOf(expectedEvents);
        assertThat(evaluation.answer().freeTimes()).isEmpty();
        assertThat(evaluation.factCheck().isPass())
                .withFailMessage(() -> evaluationFailureDetails(
                        request.history().getFirst().text(),
                        expectedSchedule,
                        evaluation.answer().message(),
                        evaluation.factCheck().getFeedback()
                ))
                .isTrue();
    }

    private String evaluationFailureDetails(
            String question,
            String reference,
            String assistantAnswer,
            String judgeFeedback
    ) {
        return """
                Calendar assistant evaluation failed.
                question: %s
                evaluation reference:
                %s
                assistant answer:
                %s
                judge feedback: %s
                """.formatted(question, reference, assistantAnswer, judgeFeedback);
    }

    private EventResponse timedEvent(
            String title,
            String description,
            String startAt,
            String endAt,
            String timeZone
    ) {
        return new EventResponse(
                1L,
                title,
                description,
                Instant.parse(startAt),
                Instant.parse(endAt),
                false,
                timeZone,
                false,
                null,
                false,
                null,
                null,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z")
        );
    }

    private EventResponse allDayEvent(String title, String startAt, String endAt) {
        return new EventResponse(
                1L,
                title,
                null,
                Instant.parse(startAt),
                Instant.parse(endAt),
                true,
                null,
                false,
                null,
                false,
                null,
                null,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z")
        );
    }

    private record AgendaEvaluation(
            CalendarAssistantAnswer answer,
            EvaluationResponse factCheck
    ) {
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedEvaluationClock() {
            return Clock.fixed(EVALUATION_NOW, ZoneOffset.UTC);
        }
    }
}
