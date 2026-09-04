package com.calio.calendar.aicalendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.aicalendar.domain.CalendarMutationScope;
import com.calio.calendar.aicalendar.domain.CalendarMutationType;
import com.calio.calendar.aicalendar.domain.CalendarMutationOperation;
import com.calio.calendar.aicalendar.service.tool.dto.CalendarMutationToolRequest;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.controller.dto.CreateEventRequest;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.controller.dto.UpdateEventRequest;
import com.calio.calendar.event.service.EventService;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceOccurrenceRequest;
import com.calio.calendar.recurrence.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.service.RecurrenceEventService;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.service.TagService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CalendarMutationServiceTest {

    @Mock
    private EventService eventService;

    @Mock
    private RecurrenceEventService recurrenceEventService;

    @Mock
    private TagService tagService;

    @Test
    @DisplayName("일정 수정 Preview는 기존 일정을 조회하지만 실제 수정은 실행하지 않는다")
    void givenEventUpdate_whenPreview_thenReturnsBeforeAndAfterWithoutChangingEvent() {
        // given
        EventResponse existingEvent = event("기존 회의", Instant.parse("2026-08-21T05:00:00Z"));
        when(eventService.getEvent(1L, 10L)).thenReturn(existingEvent);
        when(tagService.getTagOrDefault(1L, 1L)).thenReturn(Tag.personalDefault("업무", "#64748B"));

        // when
        var preview = service().preview(1L, updateRequest());

        // then
        assertThat(preview.type()).isEqualTo(CalendarMutationType.UPDATE);
        assertThat(preview.scope()).isEqualTo(CalendarMutationScope.EVENT);
        assertThat(preview.before()).isEqualTo(existingEvent);
        assertThat(preview.after().title()).isEqualTo("변경 회의");
        assertThat(preview.after().startAt()).isEqualTo(Instant.parse("2026-08-21T06:00:00Z"));
        verify(eventService, never()).updateEvent(any(), any(), any());
    }

    @Test
    @DisplayName("확정된 일정 수정은 기존 EventService 수정 유스케이스를 호출한다")
    void givenConfirmedEventUpdate_whenApply_thenDelegatesToExistingEventService() {
        // given
        when(eventService.getEvent(1L, 10L)).thenReturn(event("기존 회의", Instant.parse("2026-08-21T05:00:00Z")));
        EventResponse updatedEvent = event("변경 회의", Instant.parse("2026-08-21T06:00:00Z"));
        when(eventService.updateEvent(eq(1L), eq(10L), any()))
                .thenReturn(updatedEvent);

        // when
        List<EventResponse> result = service().apply(1L, updateRequest());

        // then
        assertThat(result).containsExactly(updatedEvent);
        ArgumentCaptor<UpdateEventRequest> requestCaptor = ArgumentCaptor.forClass(UpdateEventRequest.class);
        verify(eventService).updateEvent(eq(1L), eq(10L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().title()).isEqualTo("변경 회의");
        assertThat(requestCaptor.getValue().startAt()).isEqualTo(Instant.parse("2026-08-21T06:00:00Z"));
    }

    @Test
    @DisplayName("일정 생성 Preview는 생성하지 않고 생성될 일정을 반환한다")
    void givenEventCreation_whenPreview_thenReturnsAfterWithoutCreatingEvent() {
        // given
        when(tagService.getTagOrDefault(1L, 1L)).thenReturn(Tag.personalDefault("업무", "#64748B"));

        // when
        var preview = service().preview(1L, createRequest());

        // then
        assertThat(preview.type()).isEqualTo(CalendarMutationType.CREATE);
        assertThat(preview.scope()).isEqualTo(CalendarMutationScope.EVENT);
        assertThat(preview.before()).isNull();
        assertThat(preview.after().title()).isEqualTo("새 회의");
        verify(eventService, never()).createEvent(any(), any());
    }

    @Test
    @DisplayName("확정된 일정 생성은 기존 EventService 생성 유스케이스를 호출한다")
    void givenConfirmedEventCreation_whenApply_thenDelegatesToExistingEventService() {
        // given
        EventResponse createdEvent = event("새 회의", Instant.parse("2026-08-22T05:00:00Z"));
        when(eventService.createEvent(eq(1L), any())).thenReturn(createdEvent);

        // when
        List<EventResponse> result = service().apply(1L, createRequest());

        // then
        assertThat(result).containsExactly(createdEvent);
        ArgumentCaptor<CreateEventRequest> requestCaptor = ArgumentCaptor.forClass(CreateEventRequest.class);
        verify(eventService).createEvent(eq(1L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().title()).isEqualTo("새 회의");
    }

    @Test
    @DisplayName("반복 일정 생성 Preview는 단일 RRULE을 만들지만 저장하지 않는다")
    void givenRecurrenceCreation_whenPreview_thenReturnsEntireSeriesPreviewWithoutCreating() {
        when(tagService.getTagOrDefault(1L, 1L)).thenReturn(Tag.personalDefault("업무", "#64748B"));

        var preview = service().preview(1L, recurrenceCreateRequest());

        assertThat(preview.type()).isEqualTo(CalendarMutationType.CREATE);
        assertThat(preview.scope()).isEqualTo(CalendarMutationScope.ENTIRE_SERIES);
        assertThat(preview.before()).isNull();
        assertThat(preview.after().title()).isEqualTo("매주 회의");
        assertThat(preview.recurrence().before()).isEmpty();
        assertThat(preview.recurrence().after()).containsExactly("RRULE:FREQ=WEEKLY;UNTIL=20261225T091800Z");
        verify(recurrenceEventService, never()).createRecurrenceEvent(any(), any());
    }

    @Test
    @DisplayName("종일 반복 일정 생성 Preview는 UTC 자정 규약과 날짜 UNTIL을 사용한다")
    void givenAllDayRecurrenceCreation_whenPreview_thenUsesCanonicalAllDaySchedule() {
        when(tagService.getTagOrDefault(1L, null)).thenReturn(Tag.personalDefault("업무", "#64748B"));
        CalendarMutationToolRequest request = new CalendarMutationToolRequest(
                CalendarMutationOperation.CREATE_RECURRENCE_EVENT,
                null, null, null,
                "종일 반복", null,
                Instant.parse("2026-08-07T00:00:00Z"),
                Instant.parse("2026-08-08T00:00:00Z"),
                true, null, null, List.of("RRULE:FREQ=DAILY;UNTIL=20260831")
        );

        var preview = service().preview(1L, request);

        assertThat(preview.after().allDay()).isTrue();
        assertThat(preview.after().timeZone()).isNull();
        assertThat(preview.recurrence().after()).containsExactly("RRULE:FREQ=DAILY;UNTIL=20260831");
        verify(recurrenceEventService, never()).createRecurrenceEvent(any(), any());
    }

    @Test
    @DisplayName("확정된 반복 일정 생성은 기존 RecurrenceEventService를 호출한다")
    void givenConfirmedRecurrenceCreation_whenApply_thenDelegatesToRecurrenceEventService() {
        when(recurrenceEventService.createRecurrenceEvent(eq(1L), any())).thenReturn(createdRecurrenceSeries());

        List<EventResponse> result = service().apply(1L, recurrenceCreateRequest());

        assertThat(result).singleElement().satisfies(event -> {
            assertThat(event.recurrenceId()).isEqualTo(30L);
            assertThat(event.isRecurrenceOccurrence()).isTrue();
        });
        ArgumentCaptor<CreateRecurrenceEventRequest> captor = ArgumentCaptor.forClass(CreateRecurrenceEventRequest.class);
        verify(recurrenceEventService).createRecurrenceEvent(eq(1L), captor.capture());
        assertThat(captor.getValue().recurrence()).containsExactly("RRULE:FREQ=WEEKLY;UNTIL=20261225T091800Z");
    }

    @Test
    @DisplayName("일정 삭제 Preview는 삭제하지 않고 삭제될 일정을 반환한다")
    void givenEventDeletion_whenPreview_thenReturnsBeforeWithoutDeletingEvent() {
        // given
        EventResponse existingEvent = event("기존 회의", Instant.parse("2026-08-21T05:00:00Z"));
        when(eventService.getEvent(1L, 10L)).thenReturn(existingEvent);

        // when
        var preview = service().preview(1L, deleteRequest());

        // then
        assertThat(preview.type()).isEqualTo(CalendarMutationType.DELETE);
        assertThat(preview.scope()).isEqualTo(CalendarMutationScope.EVENT);
        assertThat(preview.before()).isEqualTo(existingEvent);
        assertThat(preview.after()).isNull();
        verify(eventService, never()).deleteEvent(any(), any());
    }

    @Test
    @DisplayName("확정된 일정 삭제는 기존 EventService 삭제 유스케이스를 호출한다")
    void givenConfirmedEventDeletion_whenApply_thenDelegatesToExistingEventService() {
        // when
        List<EventResponse> result = service().apply(1L, deleteRequest());

        // then
        assertThat(result).isEmpty();
        verify(eventService).deleteEvent(1L, 10L);
    }

    @Test
    @DisplayName("변경 operation이 없으면 validation failure로 거부한다")
    void givenMissingMutationOperation_whenPreview_thenRejectsRequest() {
        assertThatThrownBy(() -> service().preview(1L, new CalendarMutationToolRequest(
                null, null, null, null, null, null, null, null, null, null, null, null
        ))).isInstanceOf(CalioException.class)
                .extracting(exception -> ((CalioException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("필수 event ID가 없으면 수정과 삭제를 validation failure로 거부한다")
    void givenMissingEventId_whenPreview_thenRejectsUpdateAndDeletion() {
        assertThatThrownBy(() -> service().preview(1L, new CalendarMutationToolRequest(
                CalendarMutationOperation.UPDATE_EVENT, null, null, null, "변경 회의", null, null, null, null, null, null, null
        ))).isInstanceOf(CalioException.class)
                .extracting(exception -> ((CalioException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> service().preview(1L, new CalendarMutationToolRequest(
                CalendarMutationOperation.DELETE_EVENT, null, null, null, null, null, null, null, null, null, null, null
        ))).isInstanceOf(CalioException.class)
                .extracting(exception -> ((CalioException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("존재하지 않는 일정의 변경 Preview는 event not found를 전파한다")
    void givenMissingEvent_whenPreviewUpdate_thenPropagatesEventNotFound() {
        // given
        when(eventService.getEvent(1L, 10L)).thenThrow(new CalioException(ErrorCode.EVENT_NOT_FOUND));

        // when, then
        assertThatThrownBy(() -> service().preview(1L, updateRequest()))
                .isInstanceOf(CalioException.class)
                .extracting(exception -> ((CalioException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    @DisplayName("확정된 반복 회차 수정은 기존 RecurrenceEventService 수정 유스케이스를 호출한다")
    void givenConfirmedRecurrenceOccurrenceUpdate_whenApply_thenDelegatesToExistingRecurrenceService() {
        // given
        EventResponse existingOccurrence = recurrenceOccurrence(
                "기존 회의",
                Instant.parse("2026-08-21T05:00:00Z")
        );
        EventResponse updatedOccurrence = recurrenceOccurrence(
                "변경 회의",
                Instant.parse("2026-08-21T06:00:00Z")
        );
        when(recurrenceEventService.getRecurrenceOccurrence(
                1L,
                20L,
                Instant.parse("2026-08-21T05:00:00Z")
        )).thenReturn(existingOccurrence);
        when(recurrenceEventService.updateRecurrenceOccurrence(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(updatedOccurrence);

        // when
        List<EventResponse> result = service().apply(1L, occurrenceUpdateRequest());

        // then
        assertThat(result).containsExactly(updatedOccurrence);
        ArgumentCaptor<UpdateRecurrenceOccurrenceRequest> requestCaptor = ArgumentCaptor.forClass(
                UpdateRecurrenceOccurrenceRequest.class
        );
        verify(recurrenceEventService).updateRecurrenceOccurrence(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(20L),
                requestCaptor.capture()
        );
        assertThat(requestCaptor.getValue().originStartAt())
                .isEqualTo(Instant.parse("2026-08-21T05:00:00Z"));
        assertThat(requestCaptor.getValue().startAt())
                .isEqualTo(Instant.parse("2026-08-21T06:00:00Z"));
        verify(eventService, never()).listEvents(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @DisplayName("반복 회차 태그 변경 Preview는 지원하지 않는 변경 오류를 반환한다")
    void givenOccurrenceTagChange_whenPreview_thenRejectsUnsupportedChange() {
        // given
        EventResponse existingOccurrence = recurrenceOccurrence(
                "기존 회의",
                Instant.parse("2026-08-21T05:00:00Z")
        );
        when(recurrenceEventService.getRecurrenceOccurrence(
                1L,
                20L,
                Instant.parse("2026-08-21T05:00:00Z")
        )).thenReturn(existingOccurrence);
        CalendarMutationToolRequest request = new CalendarMutationToolRequest(
                CalendarMutationOperation.UPDATE_RECURRENCE_OCCURRENCE,
                null,
                20L,
                Instant.parse("2026-08-21T05:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                99L,
                null
        );

        // when, then
        assertThatThrownBy(() -> service().preview(1L, request))
                .isInstanceOf(CalioException.class)
                .extracting(exception -> ((CalioException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RECURRENCE_OCCURRENCE_TAG_CHANGE_NOT_SUPPORTED);
    }

    @Test
    @DisplayName("확정된 반복 회차 삭제는 원래 회차 시작 시각으로 삭제 유스케이스를 호출한다")
    void givenConfirmedRecurrenceOccurrenceDeletion_whenApply_thenDelegatesToRecurrenceService() {
        // given
        Instant originStartAt = Instant.parse("2026-08-21T05:00:00Z");

        // when
        List<EventResponse> result = service().apply(1L, occurrenceDeletionRequest(20L, originStartAt));

        // then
        assertThat(result).isEmpty();
        verify(recurrenceEventService).deleteRecurrenceOccurrence(1L, 20L, originStartAt);
    }

    @Test
    @DisplayName("확정된 전체 반복 일정 삭제는 시리즈 삭제 유스케이스를 호출한다")
    void givenConfirmedRecurrenceSeriesDeletion_whenApply_thenDelegatesToRecurrenceService() {
        // when
        List<EventResponse> result = service().apply(1L, seriesDeletionRequest(20L));

        // then
        assertThat(result).isEmpty();
        verify(recurrenceEventService).deleteRecurrenceEvent(1L, 20L);
    }

    @Test
    @DisplayName("반복 회차 작업에 recurrenceId 또는 originStartAt이 없으면 거절한다")
    void givenMissingOccurrenceIdentifier_whenPreview_thenRejectsValidationFailure() {
        // when, then
        assertThatThrownBy(() -> service().preview(
                1L,
                occurrenceDeletionRequest(null, Instant.parse("2026-08-21T05:00:00Z"))
        ))
                .isInstanceOf(CalioException.class)
                .extracting(exception -> ((CalioException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> service().preview(1L, occurrenceDeletionRequest(20L, null)))
                .isInstanceOf(CalioException.class)
                .extracting(exception -> ((CalioException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("전체 반복 일정 작업에 recurrenceId가 없으면 거절한다")
    void givenMissingSeriesIdentifier_whenPreview_thenRejectsValidationFailure() {
        // when, then
        assertThatThrownBy(() -> service().preview(1L, seriesDeletionRequest(null)))
                .isInstanceOf(CalioException.class)
                .extracting(exception -> ((CalioException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("존재하지 않는 반복 회차는 Preview에서 not-found 오류를 유지한다")
    void givenUnknownOccurrence_whenPreview_thenPropagatesNotFoundError() {
        // given
        Instant originStartAt = Instant.parse("2026-08-21T05:00:00Z");
        when(recurrenceEventService.getRecurrenceOccurrence(1L, 20L, originStartAt))
                .thenThrow(new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND));

        // when, then
        assertThatThrownBy(() -> service().preview(1L, occurrenceDeletionRequest(20L, originStartAt)))
                .isInstanceOf(CalioException.class)
                .extracting(exception -> ((CalioException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
    }

    @Test
    @DisplayName("전체 반복 일정 수정 Preview는 변경 전후 recurrence rule을 함께 반환한다")
    void givenSeriesRuleChange_whenPreview_thenIncludesBeforeAndAfterRules() {
        // given
        RecurrenceEventResponse existingSeries = new RecurrenceEventResponse(
                20L,
                "기존 회의",
                "기존 설명",
                false,
                Instant.parse("2026-08-21T05:00:00Z"),
                Instant.parse("2026-08-21T06:00:00Z"),
                "Asia/Seoul",
                List.of("RRULE:FREQ=WEEKLY;BYDAY=FR"),
                null,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                true
        );
        when(recurrenceEventService.getRecurrenceEvent(1L, 20L)).thenReturn(existingSeries);
        CalendarMutationToolRequest request = new CalendarMutationToolRequest(
                CalendarMutationOperation.UPDATE_RECURRENCE_SERIES,
                null,
                20L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("RRULE:FREQ=DAILY")
        );

        // when
        var preview = service().preview(1L, request);

        // then
        assertThat(preview.scope()).isEqualTo(CalendarMutationScope.ENTIRE_SERIES);
        assertThat(preview.recurrence().before()).containsExactly("RRULE:FREQ=WEEKLY;BYDAY=FR");
        assertThat(preview.recurrence().after()).containsExactly("RRULE:FREQ=DAILY");
    }

    @Test
    @DisplayName("전체 반복 일정 수정에서 recurrence rule을 생략하면 기존 규칙을 유지한다")
    void givenOmittedSeriesRules_whenPreview_thenPreservesExistingRules() {
        // given
        RecurrenceEventResponse existingSeries = recurrenceSeries();
        when(recurrenceEventService.getRecurrenceEvent(1L, 20L)).thenReturn(existingSeries);
        CalendarMutationToolRequest request = seriesUpdateRequest(null);

        // when
        var preview = service().preview(1L, request);

        // then
        assertThat(preview.recurrence().after()).containsExactlyElementsOf(existingSeries.recurrence());
    }

    @Test
    @DisplayName("전체 반복 일정 수정에서 빈 recurrence rule은 Preview와 적용 모두 거절한다")
    void givenEmptySeriesRules_whenPreviewOrApply_thenRejectsValidationFailure() {
        // given
        when(recurrenceEventService.getRecurrenceEvent(1L, 20L)).thenReturn(recurrenceSeries());
        CalendarMutationToolRequest request = seriesUpdateRequest(List.of());

        // when, then
        assertThatThrownBy(() -> service().preview(1L, request))
                .isInstanceOf(CalioException.class)
                .extracting(exception -> ((CalioException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> service().apply(1L, request))
                .isInstanceOf(CalioException.class)
                .extracting(exception -> ((CalioException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    private CalendarMutationService service() {
        return new CalendarMutationService(
                eventService,
                recurrenceEventService,
                tagService,
                new CalendarAiMutationPolicy(
                        Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC)
                )
        );
    }

    private CalendarMutationToolRequest updateRequest() {
        return new CalendarMutationToolRequest(
                CalendarMutationOperation.UPDATE_EVENT,
                10L,
                null,
                null,
                "변경 회의",
                "변경된 설명",
                Instant.parse("2026-08-21T06:00:00Z"),
                Instant.parse("2026-08-21T07:00:00Z"),
                false,
                "Asia/Seoul",
                1L,
                null
        );
    }

    private CalendarMutationToolRequest createRequest() {
        return new CalendarMutationToolRequest(
                CalendarMutationOperation.CREATE_EVENT,
                null,
                null,
                null,
                "새 회의",
                "새 회의 설명",
                Instant.parse("2026-08-22T05:00:00Z"),
                Instant.parse("2026-08-22T06:00:00Z"),
                false,
                "Asia/Seoul",
                1L,
                null
        );
    }

    private CalendarMutationToolRequest recurrenceCreateRequest() {
        return new CalendarMutationToolRequest(
                CalendarMutationOperation.CREATE_RECURRENCE_EVENT,
                null, null, null,
                "매주 회의", null,
                Instant.parse("2026-08-07T09:18:00Z"),
                Instant.parse("2026-08-07T10:18:00Z"),
                false, "UTC", 1L, List.of("RRULE:FREQ=WEEKLY;UNTIL=20261225T091800Z")
        );
    }

    private CalendarMutationToolRequest deleteRequest() {
        return new CalendarMutationToolRequest(
                CalendarMutationOperation.DELETE_EVENT,
                10L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private CalendarMutationToolRequest occurrenceUpdateRequest() {
        return new CalendarMutationToolRequest(
                CalendarMutationOperation.UPDATE_RECURRENCE_OCCURRENCE,
                null,
                20L,
                Instant.parse("2026-08-21T05:00:00Z"),
                "변경 회의",
                "변경된 설명",
                Instant.parse("2026-08-21T06:00:00Z"),
                Instant.parse("2026-08-21T07:00:00Z"),
                false,
                "Asia/Seoul",
                null,
                null
        );
    }

    private CalendarMutationToolRequest seriesUpdateRequest(List<String> recurrenceRules) {
        return new CalendarMutationToolRequest(
                CalendarMutationOperation.UPDATE_RECURRENCE_SERIES,
                null,
                20L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                recurrenceRules
        );
    }

    private CalendarMutationToolRequest occurrenceDeletionRequest(Long recurrenceId, Instant originStartAt) {
        return new CalendarMutationToolRequest(
                CalendarMutationOperation.DELETE_RECURRENCE_OCCURRENCE,
                null,
                recurrenceId,
                originStartAt,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private CalendarMutationToolRequest seriesDeletionRequest(Long recurrenceId) {
        return new CalendarMutationToolRequest(
                CalendarMutationOperation.DELETE_RECURRENCE_SERIES,
                null,
                recurrenceId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private RecurrenceEventResponse recurrenceSeries() {
        return new RecurrenceEventResponse(
                20L,
                "기존 회의",
                "기존 설명",
                false,
                Instant.parse("2026-08-21T05:00:00Z"),
                Instant.parse("2026-08-21T06:00:00Z"),
                "Asia/Seoul",
                List.of("RRULE:FREQ=WEEKLY;BYDAY=FR"),
                null,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                true
        );
    }

    private RecurrenceEventResponse createdRecurrenceSeries() {
        return new RecurrenceEventResponse(
                30L, "매주 회의", null, false,
                Instant.parse("2026-08-07T09:18:00Z"), Instant.parse("2026-08-07T10:18:00Z"),
                "UTC", List.of("RRULE:FREQ=WEEKLY;UNTIL=20261225T091800Z"), null,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z"), true
        );
    }

    private EventResponse event(String title, Instant startAt) {
        return new EventResponse(
                10L,
                title,
                "기존 설명",
                startAt,
                startAt.plusSeconds(3600),
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

    private EventResponse recurrenceOccurrence(String title, Instant startAt) {
        return new EventResponse(
                null,
                title,
                "기존 설명",
                startAt,
                startAt.plusSeconds(3600),
                false,
                "Asia/Seoul",
                false,
                20L,
                true,
                null,
                Instant.parse("2026-08-21T05:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z")
        );
    }
}
