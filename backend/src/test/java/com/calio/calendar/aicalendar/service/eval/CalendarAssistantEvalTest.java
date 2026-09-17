package com.calio.calendar.aicalendar.service.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.calio.calendar.aicalendar.controller.dto.SendCalendarConversationMessageResponse;
import com.calio.calendar.aicalendar.domain.CalendarConversationMessageRole;
import com.calio.calendar.aicalendar.domain.CalendarMutationOperation;
import com.calio.calendar.aicalendar.domain.CalendarMutationScope;
import com.calio.calendar.aicalendar.domain.CalendarMutationType;
import com.calio.calendar.aicalendar.service.CalendarMutationService;
import com.calio.calendar.aicalendar.service.SpringAiCalendarAssistantAgent;
import com.calio.calendar.aicalendar.service.dto.CalendarAssistantAnswer;
import com.calio.calendar.aicalendar.service.dto.CalendarAssistantRequest;
import com.calio.calendar.aicalendar.service.dto.CalendarConversationHistoryMessage;
import com.calio.calendar.aicalendar.service.dto.CalendarMutationPreview;
import com.calio.calendar.aicalendar.service.dto.CalendarMutationRecurrencePreview;
import com.calio.calendar.aicalendar.service.tool.dto.CalendarMutationToolRequest;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.service.EventService;
import com.calio.calendar.event.service.dto.CalendarFreeTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
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
    private SpringAiCalendarAssistantAgent assistantAgent;

    @Autowired
    private ChatModel chatModel;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private CalendarMutationService mutationService;

    private CalendarAssistantAnswer lastAssistantAnswer;

    @AfterEach
    void printAssistantResponse(TestInfo testInfo) {
        if (lastAssistantAnswer == null) {
            return;
        }
        System.out.printf(
                """
                AI calendar eval response.
                test: %s
                response: %s%n
                """,
                testInfo.getDisplayName(),
                SendCalendarConversationMessageResponse.from("eval-conversation", lastAssistantAnswer)
        );
    }

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
        CalendarAssistantAnswer answer = requestAnswer(request);
        EvaluationResponse factCheck = evaluateFacts(request.history().getFirst().text(), expectedFreeTimes, answer.message());

        // then
        assertThat(answer.events()).isEmpty();
        assertThat(answer.freeTimes())
                .withFailMessage(() -> evaluationFailureDetails(
                        request.history().getFirst().text(),
                        expectedFreeTimes,
                        answer,
                        factCheck.getFeedback()
                ))
                .containsExactlyElementsOf(freeTimes);
        assertThat(factCheck.isPass())
                .withFailMessage(() -> evaluationFailureDetails(
                        request.history().getFirst().text(),
                        expectedFreeTimes,
                        answer,
                        factCheck.getFeedback()
                ))
                .isTrue();
        verify(eventService).findAvailableTimes(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("일정 생성 요청은 실제 생성 없이 Mutation Preview를 반환한다")
    void givenEventCreationRequest_whenEvaluate_thenReturnsPreviewWithoutApplyingChange() {
        // given
        CalendarMutationPreview preview = creationPreview();
        when(mutationService.preview(any(), any())).thenReturn(preview);

        // when
        CalendarAssistantAnswer answer = requestAnswer(request("내일 오후 2시부터 3시까지 팀 회의 만들어줘"));

        // then
        assertThat(answer.mutationPreviews()).containsExactly(preview);
        assertThat(answer.events()).isEmpty();
        assertThat(answer.freeTimes()).isEmpty();
        assertMutationOperation(CalendarMutationOperation.CREATE_EVENT);
        verify(mutationService, never()).apply(any(), any());
        verifyNoInteractions(eventService);
    }

    @Test
    @DisplayName("종일 일정 생성 요청은 UTC 자정과 exclusive end로만 Preview한다")
    void givenAllDayEventCreationRequest_whenEvaluate_thenPreviewsCanonicalUtcMidnightRange() {
        // given
        CalendarMutationPreview preview = new CalendarMutationPreview(
                CalendarMutationType.CREATE,
                CalendarMutationScope.EVENT,
                null,
                allDayEvent("광복절", "2026-08-16T00:00:00Z", "2026-08-17T00:00:00Z")
        );
        when(mutationService.preview(any(), any())).thenReturn(preview);

        // when
        CalendarAssistantAnswer answer = requestAnswer(request("내일 광복절 종일 일정 만들어줘"));

        // then
        assertThat(answer.mutationPreviews()).containsExactly(preview);
        CalendarMutationToolRequest mutationRequest = assertMutationOperation(
                CalendarMutationOperation.CREATE_EVENT
        );
        assertThat(mutationRequest.allDay()).isTrue();
        assertThat(mutationRequest.startAt()).isEqualTo(Instant.parse("2026-08-16T00:00:00Z"));
        assertThat(mutationRequest.endAt()).isEqualTo(Instant.parse("2026-08-17T00:00:00Z"));
        assertThat(mutationRequest.timeZone()).isNull();
        verify(mutationService, never()).apply(any(), any());
    }

    @Test
    @DisplayName("지원 범위의 반복 일정 생성 요청은 전체 시리즈 Mutation Preview를 반환한다")
    void givenSupportedRecurrenceCreationRequest_whenEvaluate_thenReturnsSeriesPreview() {
        CalendarMutationPreview preview = new CalendarMutationPreview(
                CalendarMutationType.CREATE,
                CalendarMutationScope.ENTIRE_SERIES,
                null,
                recurrenceOccurrence("팀 회의", "2026-08-21T09:18:00Z", "2026-08-21T10:18:00Z"),
                new CalendarMutationRecurrencePreview(List.of(), List.of("RRULE:FREQ=WEEKLY;UNTIL=20261225T091800Z"))
        );
        when(mutationService.preview(any(), any())).thenReturn(preview);

        CalendarAssistantAnswer answer = requestAnswer(request(
                "2026년 8월 21일부터 2026년 12월 25일까지 매주 금요일 오후 6시 18분에 팀 회의 반복 일정 만들어줘"
        ));

        assertThat(answer.mutationPreviews()).containsExactly(preview);
        CalendarMutationToolRequest mutationRequest = assertMutationOperation(
                CalendarMutationOperation.CREATE_RECURRENCE_EVENT
        );
        assertThat(mutationRequest.recurrenceRules())
                .containsExactly("RRULE:FREQ=WEEKLY;UNTIL=20261225T091800Z");
        verify(mutationService, never()).apply(any(), any());
    }

    @Test
    @DisplayName("지원하지 않는 반복 생성 요청은 Mutation Preview를 만들지 않는다")
    void givenUnsupportedRecurrenceCreationRequests_whenEvaluate_thenDoesNotPreview() {
        List<String> requests = List.of(
                "지금부터 3000년 이후의 8월 28일까지 매주 금요일 오후 6시 18분에 진행하는 반복 일정을 만들어줘",
                "27년도 1월 1일날 부터 12월 31일까지 00시 부터 24시까지 1초 단위로 일정을 만들어줘",
                "1.5초짜리의 반복 일정을 만들어줘"
        );

        for (String request : requests) {
            CalendarAssistantAnswer answer = requestAnswer(request(request));
            assertThat(answer.mutationPreviews()).isEmpty();
        }
        verify(mutationService, never()).preview(any(), any());
        verify(mutationService, never()).apply(any(), any());
    }

    @Test
    @DisplayName("대상이 하나인 일정 수정 요청은 변경 전후 Mutation Preview를 반환한다")
    void givenSingleMatchingEventUpdateRequest_whenEvaluate_thenReturnsUpdatePreview() {
        // given
        EventResponse before = timedEvent(
                "팀 회의",
                null,
                "2026-08-16T05:00:00Z",
                "2026-08-16T06:00:00Z",
                "Asia/Seoul"
        );
        EventResponse after = timedEvent(
                "팀 회의",
                null,
                "2026-08-16T06:00:00Z",
                "2026-08-16T07:00:00Z",
                "Asia/Seoul"
        );
        CalendarMutationPreview preview = new CalendarMutationPreview(
                CalendarMutationType.UPDATE,
                CalendarMutationScope.EVENT,
                before,
                after
        );
        when(eventService.listEvents(any(), any(), any())).thenReturn(List.of(before));
        when(mutationService.preview(any(), any())).thenReturn(preview);

        // when
        CalendarAssistantAnswer answer = requestAnswer(request("내일 팀 회의를 오후 3시로 옮겨줘"));

        // then
        assertThat(answer.events()).containsExactly(before);
        assertThat(answer.mutationPreviews()).containsExactly(preview);
        assertMutationOperation(CalendarMutationOperation.UPDATE_EVENT);
        verify(mutationService, never()).apply(any(), any());
    }

    @Test
    @DisplayName("여러 일정이 일치하는 삭제 요청은 후보를 반환하고 Preview를 만들지 않는다")
    void givenMultipleMatchingEventDeletionRequest_whenEvaluate_thenReturnsCandidatesWithoutPreview() {
        // given
        EventResponse firstEvent = timedEvent(
                "팀 회의",
                null,
                "2026-08-16T05:00:00Z",
                "2026-08-16T06:00:00Z",
                "Asia/Seoul"
        );
        EventResponse secondEvent = timedEvent(
                "팀 회의",
                null,
                "2026-08-16T08:00:00Z",
                "2026-08-16T09:00:00Z",
                "Asia/Seoul"
        );
        when(eventService.listEvents(any(), any(), any())).thenReturn(List.of(firstEvent, secondEvent));

        // when
        CalendarAssistantAnswer answer = requestAnswer(request("내일 팀 회의 삭제해줘"));

        // then
        assertThat(answer.events()).containsExactly(firstEvent, secondEvent);
        assertThat(answer.mutationPreviews()).isEmpty();
        verifyNoInteractions(mutationService);
    }

    @Test
    @DisplayName("반복 일정 변경 범위가 없으면 Preview 없이 범위를 되묻는다")
    void givenRecurringEventUpdateWithoutScope_whenEvaluate_thenAsksForScopeWithoutPreview() {
        // when
        CalendarAssistantAnswer answer = requestAnswer(request("매주 팀 회의를 오후 3시로 옮겨줘"));

        // then
        assertThat(answer.events()).isEmpty();
        assertThat(answer.mutationPreviews()).isEmpty();
        verifyNoInteractions(mutationService);
    }

    @Test
    @DisplayName("이번 반복 회차 변경 요청은 THIS_OCCURRENCE Mutation Preview를 반환한다")
    void givenSingleRecurrenceOccurrenceUpdateRequest_whenEvaluate_thenReturnsOccurrencePreview() {
        // given
        EventResponse before = recurrenceOccurrence(
                "팀 회의",
                "2026-08-16T05:00:00Z",
                "2026-08-16T06:00:00Z"
        );
        EventResponse after = recurrenceOccurrence(
                "팀 회의",
                "2026-08-16T06:00:00Z",
                "2026-08-16T07:00:00Z"
        );
        CalendarMutationPreview preview = new CalendarMutationPreview(
                CalendarMutationType.UPDATE,
                CalendarMutationScope.THIS_OCCURRENCE,
                before,
                after
        );
        when(eventService.listEvents(any(), any(), any())).thenReturn(List.of(before));
        when(mutationService.preview(any(), any())).thenReturn(preview);

        // when
        CalendarAssistantAnswer answer = requestAnswer(
                request("내일의 반복 일정인 ‘팀 회의’를 이번 회차만 오후 3시로 옮겨줘")
        );

        // then
        assertThat(answer.mutationPreviews()).containsExactly(preview);
        CalendarMutationToolRequest mutationRequest = assertMutationOperation(
                CalendarMutationOperation.UPDATE_RECURRENCE_OCCURRENCE
        );
        assertThat(mutationRequest.recurrenceId()).isEqualTo(10L);
        assertThat(mutationRequest.originStartAt()).isEqualTo(Instant.parse("2026-08-16T05:00:00Z"));
        verify(mutationService, never()).apply(any(), any());
    }

    @Test
    @DisplayName("이번 반복 회차 태그 변경 요청은 Preview를 만들지 않고 제한을 안내한다")
    void givenSingleRecurrenceOccurrenceTagChangeRequest_whenEvaluate_thenExplainsUnsupportedChange() {
        // given
        EventResponse occurrence = recurrenceOccurrence(
                "팀 회의",
                "2026-08-16T05:00:00Z",
                "2026-08-16T06:00:00Z"
        );
        when(eventService.listEvents(any(), any(), any())).thenReturn(List.of(occurrence));

        // when
        CalendarAssistantAnswer answer = requestAnswer(
                request("내일 반복 일정인 ‘팀 회의’의 이번 회차만 태그를 업무로 바꿔줘")
        );

        // then
        assertThat(answer.mutationPreviews()).isEmpty();
        assertThat(answer.message()).contains("태그");
        verify(mutationService, never()).preview(any(), any());
        verify(mutationService, never()).apply(any(), any());
    }

    @Test
    @DisplayName("전체 반복 일정 변경 요청은 ENTIRE_SERIES Mutation Preview를 반환한다")
    void givenEntireRecurrenceSeriesUpdateRequest_whenEvaluate_thenReturnsSeriesPreview() {
        // given
        EventResponse before = recurrenceOccurrence(
                "팀 회의",
                "2026-08-16T05:00:00Z",
                "2026-08-16T06:00:00Z"
        );
        EventResponse after = recurrenceOccurrence(
                "팀 회의",
                "2026-08-16T06:00:00Z",
                "2026-08-16T07:00:00Z"
        );
        CalendarMutationPreview preview = new CalendarMutationPreview(
                CalendarMutationType.UPDATE,
                CalendarMutationScope.ENTIRE_SERIES,
                before,
                after,
                new CalendarMutationRecurrencePreview(
                        List.of("RRULE:FREQ=WEEKLY;BYDAY=SUN"),
                        List.of("RRULE:FREQ=WEEKLY;BYDAY=SUN;BYHOUR=6")
                )
        );
        when(eventService.listEvents(any(), any(), any())).thenReturn(List.of(before));
        when(mutationService.preview(any(), any())).thenReturn(preview);

        // when
        CalendarAssistantAnswer answer = requestAnswer(
                request("내일부터 매주 반복되는 팀 회의 전체를 오후 3시로 옮겨줘")
        );

        // then
        assertThat(answer.mutationPreviews()).containsExactly(preview);
        assertThat(answer.mutationPreviews()).singleElement().satisfies(seriesPreview ->
                assertThat(seriesPreview.recurrence().after())
                        .containsExactly("RRULE:FREQ=WEEKLY;BYDAY=SUN;BYHOUR=6")
        );
        CalendarMutationToolRequest mutationRequest = assertMutationOperation(
                CalendarMutationOperation.UPDATE_RECURRENCE_SERIES
        );
        assertThat(mutationRequest.recurrenceId()).isEqualTo(10L);
        assertThat(mutationRequest.originStartAt()).isNull();
        verify(mutationService, never()).apply(any(), any());
    }

    @Test
    @DisplayName("직전 Mutation Preview 뒤의 적용 요청은 기존 변경 결과를 반환한다")
    void givenPreviousMutationPreview_whenEvaluateConfirmation_thenAppliesMutation() {
        // given
        EventResponse originalEvent = timedEvent(
                "팀 회의",
                null,
                "2026-08-16T05:00:00Z",
                "2026-08-16T06:00:00Z",
                "Asia/Seoul"
        );
        EventResponse updatedEvent = timedEvent(
                "팀 회의",
                null,
                "2026-08-16T06:00:00Z",
                "2026-08-16T07:00:00Z",
                "Asia/Seoul"
        );
        CalendarMutationPreview preview = new CalendarMutationPreview(
                CalendarMutationType.UPDATE,
                CalendarMutationScope.EVENT,
                originalEvent,
                updatedEvent
        );
        when(eventService.listEvents(any(), any(), any())).thenReturn(List.of(originalEvent));
        when(mutationService.apply(eq(1L), any())).thenReturn(List.of(updatedEvent));
        CalendarAssistantRequest request = requestWithHistory(
                new CalendarConversationHistoryMessage(
                        CalendarConversationMessageRole.USER,
                        "내일 팀 회의를 오후 3시로 옮겨줘"
                ),
                mutationPreviewMessage("팀 회의를 오후 2시에서 오후 3시로 변경할까요?", preview),
                new CalendarConversationHistoryMessage(
                        CalendarConversationMessageRole.USER,
                        "적용해줘"
                )
        );

        // when
        CalendarAssistantAnswer answer = requestAnswer(request);

        // then
        assertThat(answer.events()).containsExactly(updatedEvent);
        verify(mutationService).apply(eq(1L), any());
    }

    @Test
    @DisplayName("직전 삭제 Mutation Preview 뒤의 확인 요청은 삭제를 적용하고 EVENTS 블록을 비운다")
    void givenPreviousDeletionPreview_whenEvaluateConfirmation_thenAppliesDeletionWithoutEvents() {
        // given
        EventResponse deletedEvent = timedEvent(
                "팀 회의",
                null,
                "2026-08-16T05:00:00Z",
                "2026-08-16T06:00:00Z",
                "Asia/Seoul"
        );
        CalendarMutationPreview preview = new CalendarMutationPreview(
                CalendarMutationType.DELETE,
                CalendarMutationScope.EVENT,
                deletedEvent,
                null
        );
        when(eventService.listEvents(any(), any(), any())).thenReturn(List.of(deletedEvent));
        when(mutationService.apply(eq(1L), any())).thenReturn(List.of());
        CalendarAssistantRequest request = requestWithHistory(
                new CalendarConversationHistoryMessage(
                        CalendarConversationMessageRole.USER,
                        "내일 팀 회의 삭제해줘"
                ),
                mutationPreviewMessage("내일 오후 2시 팀 회의를 삭제할까요?", preview),
                new CalendarConversationHistoryMessage(
                        CalendarConversationMessageRole.USER,
                        "응"
                )
        );

        // when
        CalendarAssistantAnswer answer = requestAnswer(request);

        // then
        assertThat(answer.events()).isEmpty();
        assertThat(answer.mutationPreviews()).isEmpty();
        verify(mutationService).apply(eq(1L), any());
    }

    @Test
    @DisplayName("직전 Mutation Preview가 없는 적용 요청은 변경을 실행하지 않는다")
    void givenNoPreviousMutationPreview_whenEvaluateConfirmation_thenDoesNotApplyMutation() {
        // given
        CalendarAssistantRequest request = requestWithHistory(
                new CalendarConversationHistoryMessage(
                        CalendarConversationMessageRole.USER,
                        "내일 일정 알려줘"
                ),
                new CalendarConversationHistoryMessage(
                        CalendarConversationMessageRole.ASSISTANT,
                        "내일은 팀 회의가 있어요."
                ),
                new CalendarConversationHistoryMessage(
                        CalendarConversationMessageRole.USER,
                        "적용해줘"
                )
        );

        // when
        CalendarAssistantAnswer answer = requestAnswer(request);

        // then
        assertThat(answer.events()).isEmpty();
        assertThat(answer.mutationPreviews()).isEmpty();
        verifyNoInteractions(mutationService);
    }

    @Test
    @DisplayName("무관한 요청은 calendar tool과 blocks 없이 지원 범위를 안내한다")
    void givenUnrelatedRequest_whenEvaluate_thenReturnsAnswerWithoutToolResults() {
        // when
        CalendarAssistantAnswer answer = requestAnswer(request("오늘 점심 메뉴 추천해줘"));

        // then
        verifyNoInteractions(eventService);
        assertThat(answer.events()).isEmpty();
        assertThat(answer.freeTimes()).isEmpty();
    }

    private CalendarAssistantRequest request(String message) {
        return request(message, "Asia/Seoul");
    }

    private CalendarAssistantRequest request(String message, String timeZone) {
        return requestWithHistory(timeZone, new CalendarConversationHistoryMessage(
                CalendarConversationMessageRole.USER,
                message
        ));
    }

    private CalendarAssistantRequest requestWithHistory(CalendarConversationHistoryMessage... history) {
        return requestWithHistory("Asia/Seoul", history);
    }

    private CalendarAssistantRequest requestWithHistory(
            String timeZone,
            CalendarConversationHistoryMessage... history
    ) {
        return new CalendarAssistantRequest(
                1L,
                "eval-conversation",
                ZoneId.of(timeZone),
                List.of(history)
        );
    }

    private CalendarConversationHistoryMessage mutationPreviewMessage(
            String text,
            CalendarMutationPreview preview
    ) {
        return new CalendarConversationHistoryMessage(
                CalendarConversationMessageRole.ASSISTANT,
                text,
                mutationPreviewBlocksJson(preview)
        );
    }

    private String mutationPreviewBlocksJson(CalendarMutationPreview preview) {
        return """
                [{"type":"MUTATION_PREVIEW","items":[{"type":"%s","scope":"%s","before":%s,"after":%s}]}]
                """.formatted(
                preview.type(),
                preview.scope(),
                mutationPreviewEventJson(preview.before()),
                mutationPreviewEventJson(preview.after())
        );
    }

    private String mutationPreviewEventJson(EventResponse event) {
        if (event == null) {
            return "null";
        }
        return """
                {"title":"%s","startAt":"%s","endAt":"%s","allDay":%s,"tag":null}
                """.formatted(
                event.title(),
                event.startAt(),
                event.endAt(),
                event.allDay()
        );
    }

    private CalendarMutationToolRequest assertMutationOperation(CalendarMutationOperation expectedOperation) {
        ArgumentCaptor<CalendarMutationToolRequest> requestCaptor = ArgumentCaptor.forClass(
                CalendarMutationToolRequest.class
        );
        verify(mutationService).preview(eq(1L), requestCaptor.capture());
        CalendarMutationToolRequest request = requestCaptor.getValue();
        assertThat(request.operation()).isEqualTo(expectedOperation);
        return request;
    }

    private FactCheckingEvaluator factCheckingEvaluator() {
        return FactCheckingEvaluator.builder(ChatClient.builder(chatModel)).build();
    }

    private CalendarAssistantAnswer requestAnswer(CalendarAssistantRequest request) {
        lastAssistantAnswer = assistantAgent.answer(request);
        assertThat(lastAssistantAnswer.message()).doesNotContain("ASSISTANT_RESPONSE_BLOCKS");
        return lastAssistantAnswer;
    }

    private AgendaEvaluation evaluateAgenda(
            CalendarAssistantRequest request,
            List<EventResponse> events,
            String expectedSchedule
    ) {
        when(eventService.listEvents(any(), any(), any())).thenReturn(events);
        CalendarAssistantAnswer answer = requestAnswer(request);
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
                        evaluation.answer(),
                        evaluation.factCheck().getFeedback()
                ))
                .containsExactlyElementsOf(expectedEvents);
        assertThat(evaluation.answer().freeTimes()).isEmpty();
        assertThat(evaluation.factCheck().isPass())
                .withFailMessage(() -> evaluationFailureDetails(
                        request.history().getFirst().text(),
                        expectedSchedule,
                        evaluation.answer(),
                        evaluation.factCheck().getFeedback()
                ))
                .isTrue();
    }

    private String evaluationFailureDetails(
            String question,
            String reference,
            CalendarAssistantAnswer assistantAnswer,
            String judgeFeedback
    ) {
        return """
                Calendar assistant evaluation failed.
                question: %s
                evaluation reference:
                %s
                assistant response:
                %s
                judge feedback: %s
                """.formatted(
                question,
                reference,
                SendCalendarConversationMessageResponse.from("eval-conversation", assistantAnswer),
                judgeFeedback
        );
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

    private CalendarMutationPreview creationPreview() {
        return new CalendarMutationPreview(
                CalendarMutationType.CREATE,
                CalendarMutationScope.EVENT,
                null,
                timedEvent(
                        "팀 회의",
                        null,
                        "2026-08-16T05:00:00Z",
                        "2026-08-16T06:00:00Z",
                        "Asia/Seoul"
                )
        );
    }

    private EventResponse recurrenceOccurrence(String title, String startAt, String endAt) {
        return new EventResponse(
                null,
                title,
                null,
                Instant.parse(startAt),
                Instant.parse(endAt),
                false,
                "Asia/Seoul",
                false,
                10L,
                true,
                null,
                Instant.parse("2026-08-16T05:00:00Z"),
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
