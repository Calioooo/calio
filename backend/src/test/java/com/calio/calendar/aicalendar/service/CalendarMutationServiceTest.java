package com.calio.calendar.aicalendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.aicalendar.domain.CalendarMutationOperation;
import com.calio.calendar.aicalendar.domain.CalendarMutationScope;
import com.calio.calendar.aicalendar.domain.CalendarMutationType;
import com.calio.calendar.aicalendar.service.dto.CalendarMutationPreview;
import com.calio.calendar.aicalendar.service.tool.dto.CalendarMutationToolRequest;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceOccurrenceRequest;
import com.calio.calendar.recurrence.usecase.CreateRecurrenceEventUseCase;
import com.calio.calendar.recurrence.usecase.DeleteRecurrenceEventUseCase;
import com.calio.calendar.recurrence.usecase.DeleteRecurrenceOccurrenceUseCase;
import com.calio.calendar.recurrence.usecase.GetRecurrenceEventUseCase;
import com.calio.calendar.recurrence.usecase.GetRecurrenceOccurrenceUseCase;
import com.calio.calendar.recurrence.usecase.UpdateRecurrenceEventUseCase;
import com.calio.calendar.recurrence.usecase.UpdateRecurrenceOccurrenceUseCase;
import com.calio.calendar.singleevent.controller.dto.CreateSingleEventRequest;
import com.calio.calendar.singleevent.controller.dto.EventResponse;
import com.calio.calendar.singleevent.controller.dto.UpdateSingleEventRequest;
import com.calio.calendar.singleevent.usecase.CreateSingleEventUseCase;
import com.calio.calendar.singleevent.usecase.DeleteSingleEventUseCase;
import com.calio.calendar.singleevent.usecase.GetSingleEventUseCase;
import com.calio.calendar.singleevent.usecase.UpdateSingleEventUseCase;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CalendarMutationServiceTest {

  @Mock private CreateSingleEventUseCase createEventUseCase;

  @Mock private GetSingleEventUseCase getEventUseCase;

  @Mock private UpdateSingleEventUseCase updateEventUseCase;

  @Mock private DeleteSingleEventUseCase deleteEventUseCase;

  @Mock private CreateRecurrenceEventUseCase createRecurrenceEvent;
  @Mock private GetRecurrenceEventUseCase getRecurrenceEvent;
  @Mock private GetRecurrenceOccurrenceUseCase getRecurrenceOccurrence;
  @Mock private UpdateRecurrenceEventUseCase updateRecurrenceEvent;
  @Mock private UpdateRecurrenceOccurrenceUseCase updateRecurrenceOccurrence;
  @Mock private DeleteRecurrenceEventUseCase deleteRecurrenceEvent;
  @Mock private DeleteRecurrenceOccurrenceUseCase deleteRecurrenceOccurrence;

  @Mock private TagRepository tagRepository;

  @Mock private CalendarAiMutationPolicy aiMutationPolicy;

  @Test
  @DisplayName("일정 수정 Preview는 기존 일정을 조회하지만 실제 수정은 실행하지 않는다")
  void givenEventUpdate_whenPreview_thenReturnsBeforeAndAfterWithoutChangingEvent() {
    EventResponse existingEvent = event("기존 회의", Instant.parse("2026-08-21T05:00:00Z"));
    when(getEventUseCase.get(1L, 10L)).thenReturn(existingEvent);
    when(tagRepository.findPersonalDefaultTagById(1L))
        .thenReturn(Optional.of(Tag.personalDefault("업무", "#64748B")));

    CalendarMutationPreview preview = service().preview(1L, updateRequest());

    assertThat(preview.type()).isEqualTo(CalendarMutationType.UPDATE);
    assertThat(preview.scope()).isEqualTo(CalendarMutationScope.EVENT);
    assertThat(preview.before()).isEqualTo(existingEvent);
    assertThat(preview.after().title()).isEqualTo("변경 회의");
    assertThat(preview.after().startAt()).isEqualTo(Instant.parse("2026-08-21T06:00:00Z"));
    verify(updateEventUseCase, never()).update(any(), any(), any());
  }

  @Test
  @DisplayName("확정된 일정 수정은 변경된 일정 응답을 반환한다")
  void givenConfirmedEventUpdate_whenApply_thenReturnsUpdatedEvent() {
    when(getEventUseCase.get(1L, 10L))
        .thenReturn(event("기존 회의", Instant.parse("2026-08-21T05:00:00Z")));
    EventResponse updatedEvent = event("변경 회의", Instant.parse("2026-08-21T06:00:00Z"));
    when(updateEventUseCase.update(eq(1L), eq(10L), any())).thenReturn(updatedEvent);

    List<EventResponse> result = service().apply(1L, updateRequest());

    assertThat(result).containsExactly(updatedEvent);
    ArgumentCaptor<UpdateSingleEventRequest> requestCaptor =
        ArgumentCaptor.forClass(UpdateSingleEventRequest.class);
    verify(updateEventUseCase).update(eq(1L), eq(10L), requestCaptor.capture());
    assertThat(requestCaptor.getValue().title()).isEqualTo("변경 회의");
    assertThat(requestCaptor.getValue().startAt()).isEqualTo(Instant.parse("2026-08-21T06:00:00Z"));
  }

  @Test
  @DisplayName("일정 생성 Preview는 생성하지 않고 생성될 일정을 반환한다")
  void givenEventCreation_whenPreview_thenReturnsAfterWithoutCreatingEvent() {
    when(tagRepository.findPersonalDefaultTagById(1L))
        .thenReturn(Optional.of(Tag.personalDefault("업무", "#64748B")));

    CalendarMutationPreview preview = service().preview(1L, createRequest());

    assertThat(preview.type()).isEqualTo(CalendarMutationType.CREATE);
    assertThat(preview.scope()).isEqualTo(CalendarMutationScope.EVENT);
    assertThat(preview.before()).isNull();
    assertThat(preview.after().title()).isEqualTo("새 회의");
    verify(createEventUseCase, never()).create(any(), any());
  }

  @Test
  @DisplayName("확정된 일정 생성은 생성된 일정 응답을 반환한다")
  void givenConfirmedEventCreation_whenApply_thenReturnsCreatedEvent() {
    EventResponse createdEvent = event("새 회의", Instant.parse("2026-08-22T05:00:00Z"));
    when(createEventUseCase.create(eq(1L), any())).thenReturn(createdEvent);

    List<EventResponse> result = service().apply(1L, createRequest());

    assertThat(result).containsExactly(createdEvent);
    ArgumentCaptor<CreateSingleEventRequest> requestCaptor =
        ArgumentCaptor.forClass(CreateSingleEventRequest.class);
    verify(createEventUseCase).create(eq(1L), requestCaptor.capture());
    assertThat(requestCaptor.getValue().title()).isEqualTo("새 회의");
  }

  @Test
  @DisplayName("AI 일정 변경 요청의 제목도 80자를 초과하면 거부한다")
  void givenOverlengthTitle_whenPreviewCreation_thenRejectsRequest() {
    CalendarMutationToolRequest request =
        new CalendarMutationToolRequest(
            CalendarMutationOperation.CREATE_EVENT,
            null,
            null,
            null,
            "a".repeat(81),
            "새 회의 설명",
            Instant.parse("2026-08-22T05:00:00Z"),
            Instant.parse("2026-08-22T06:00:00Z"),
            false,
            "Asia/Seoul",
            1L,
            null);

    assertThatThrownBy(() -> service().preview(1L, request))
        .isInstanceOf(CalioException.class)
        .extracting(exception -> ((CalioException) exception).getErrorCode())
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
  }

  @Test
  @DisplayName("AI 일정 변경 요청은 80개의 보조 문자를 제목으로 허용한다")
  void givenTitleWithEightyCodePoints_whenPreviewCreation_thenAllowsRequest() {
    CalendarMutationToolRequest request =
        new CalendarMutationToolRequest(
            CalendarMutationOperation.CREATE_EVENT,
            null,
            null,
            null,
            "😀".repeat(80),
            "새 회의 설명",
            Instant.parse("2026-08-22T05:00:00Z"),
            Instant.parse("2026-08-22T06:00:00Z"),
            false,
            "Asia/Seoul",
            1L,
            null);
    when(tagRepository.findPersonalDefaultTagById(1L))
        .thenReturn(Optional.of(Tag.personalDefault("업무", "#64748B")));

    CalendarMutationPreview preview = service().preview(1L, request);

    assertThat(preview.after().title()).isEqualTo("😀".repeat(80));
  }

  @Test
  @DisplayName("일정 삭제 Preview는 삭제하지 않고 삭제될 일정을 반환한다")
  void givenEventDeletion_whenPreview_thenReturnsBeforeWithoutDeletingEvent() {
    EventResponse existingEvent = event("기존 회의", Instant.parse("2026-08-21T05:00:00Z"));
    when(getEventUseCase.get(1L, 10L)).thenReturn(existingEvent);

    CalendarMutationPreview preview = service().preview(1L, deleteRequest());

    assertThat(preview.type()).isEqualTo(CalendarMutationType.DELETE);
    assertThat(preview.scope()).isEqualTo(CalendarMutationScope.EVENT);
    assertThat(preview.before()).isEqualTo(existingEvent);
    assertThat(preview.after()).isNull();
    verify(deleteEventUseCase, never()).delete(any(), any());
  }

  @Test
  @DisplayName("확정된 일정 삭제는 빈 결과를 반환한다")
  void givenConfirmedEventDeletion_whenApply_thenReturnsEmptyResult() {
    List<EventResponse> result = service().apply(1L, deleteRequest());

    assertThat(result).isEmpty();
    verify(deleteEventUseCase).delete(1L, 10L);
  }

  @Test
  @DisplayName("변경 operation이 없으면 validation failure로 거부한다")
  void givenMissingMutationOperation_whenPreview_thenRejectsRequest() {
    assertThatThrownBy(
            () ->
                service()
                    .preview(
                        1L,
                        new CalendarMutationToolRequest(
                            null, null, null, null, null, null, null, null, null, null, null,
                            null)))
        .isInstanceOf(CalioException.class)
        .extracting(exception -> ((CalioException) exception).getErrorCode())
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
  }

  @Test
  @DisplayName("필수 event ID가 없으면 수정과 삭제를 validation failure로 거부한다")
  void givenMissingEventId_whenPreview_thenRejectsUpdateAndDeletion() {
    assertThatThrownBy(
            () ->
                service()
                    .preview(
                        1L,
                        new CalendarMutationToolRequest(
                            CalendarMutationOperation.UPDATE_EVENT,
                            null,
                            null,
                            null,
                            "변경 회의",
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null)))
        .isInstanceOf(CalioException.class)
        .extracting(exception -> ((CalioException) exception).getErrorCode())
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
    assertThatThrownBy(
            () ->
                service()
                    .preview(
                        1L,
                        new CalendarMutationToolRequest(
                            CalendarMutationOperation.DELETE_EVENT,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null)))
        .isInstanceOf(CalioException.class)
        .extracting(exception -> ((CalioException) exception).getErrorCode())
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
  }

  @Test
  @DisplayName("존재하지 않는 일정의 변경 Preview는 event not found를 전파한다")
  void givenMissingEvent_whenPreviewUpdate_thenPropagatesEventNotFound() {
    when(getEventUseCase.get(1L, 10L)).thenThrow(new CalioException(ErrorCode.EVENT_NOT_FOUND));

    assertThatThrownBy(() -> service().preview(1L, updateRequest()))
        .isInstanceOf(CalioException.class)
        .extracting(exception -> ((CalioException) exception).getErrorCode())
        .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
  }

  @Test
  @DisplayName("확정된 반복 회차 수정은 해당 수정 유스케이스를 호출한다")
  void
      givenConfirmedRecurrenceOccurrenceUpdate_whenApply_thenDelegatesToExistingRecurrenceService() {
    EventResponse existingOccurrence =
        recurrenceOccurrence("기존 회의", Instant.parse("2026-08-21T05:00:00Z"));
    EventResponse updatedOccurrence =
        recurrenceOccurrence("변경 회의", Instant.parse("2026-08-21T06:00:00Z"));
    when(getRecurrenceOccurrence.get(1L, 20L, Instant.parse("2026-08-21T05:00:00Z")))
        .thenReturn(existingOccurrence);
    when(updateRecurrenceOccurrence.update(
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.eq(20L),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(updatedOccurrence);

    List<EventResponse> result = service().apply(1L, occurrenceUpdateRequest());

    assertThat(result).containsExactly(updatedOccurrence);
    ArgumentCaptor<UpdateRecurrenceOccurrenceRequest> requestCaptor =
        ArgumentCaptor.forClass(UpdateRecurrenceOccurrenceRequest.class);
    verify(updateRecurrenceOccurrence)
        .update(
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.eq(20L),
            requestCaptor.capture());
    assertThat(requestCaptor.getValue().originStartAt())
        .isEqualTo(Instant.parse("2026-08-21T05:00:00Z"));
    assertThat(requestCaptor.getValue().startAt()).isEqualTo(Instant.parse("2026-08-21T06:00:00Z"));
  }

  @Test
  @DisplayName("반복 회차 태그 변경 Preview는 지원하지 않는 변경 오류를 반환한다")
  void givenOccurrenceTagChange_whenPreview_thenRejectsUnsupportedChange() {
    EventResponse existingOccurrence =
        recurrenceOccurrence("기존 회의", Instant.parse("2026-08-21T05:00:00Z"));
    when(getRecurrenceOccurrence.get(1L, 20L, Instant.parse("2026-08-21T05:00:00Z")))
        .thenReturn(existingOccurrence);
    CalendarMutationToolRequest request =
        new CalendarMutationToolRequest(
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
            null);

    assertThatThrownBy(() -> service().preview(1L, request))
        .isInstanceOf(CalioException.class)
        .extracting(exception -> ((CalioException) exception).getErrorCode())
        .isEqualTo(ErrorCode.RECURRENCE_OCCURRENCE_TAG_CHANGE_NOT_SUPPORTED);
  }

  @Test
  @DisplayName("확정된 반복 회차 삭제는 원래 회차 시작 시각으로 삭제 유스케이스를 호출한다")
  void givenConfirmedRecurrenceOccurrenceDeletion_whenApply_thenDelegatesToRecurrenceService() {
    Instant originStartAt = Instant.parse("2026-08-21T05:00:00Z");

    List<EventResponse> result = service().apply(1L, occurrenceDeletionRequest(20L, originStartAt));

    assertThat(result).isEmpty();
    verify(deleteRecurrenceOccurrence).delete(1L, 20L, originStartAt);
  }

  @Test
  @DisplayName("확정된 전체 반복 일정 삭제는 시리즈 삭제 유스케이스를 호출한다")
  void givenConfirmedRecurrenceSeriesDeletion_whenApply_thenDelegatesToRecurrenceService() {
    List<EventResponse> result = service().apply(1L, seriesDeletionRequest(20L));

    assertThat(result).isEmpty();
    verify(deleteRecurrenceEvent).delete(1L, 20L);
  }

  @Test
  @DisplayName("반복 회차 작업에 recurrenceId 또는 originStartAt이 없으면 거절한다")
  void givenMissingOccurrenceIdentifier_whenPreview_thenRejectsValidationFailure() {
    assertThatThrownBy(
            () ->
                service()
                    .preview(
                        1L, occurrenceDeletionRequest(null, Instant.parse("2026-08-21T05:00:00Z"))))
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
    assertThatThrownBy(() -> service().preview(1L, seriesDeletionRequest(null)))
        .isInstanceOf(CalioException.class)
        .extracting(exception -> ((CalioException) exception).getErrorCode())
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
  }

  @Test
  @DisplayName("존재하지 않는 반복 회차는 Preview에서 not-found 오류를 유지한다")
  void givenUnknownOccurrence_whenPreview_thenPropagatesNotFoundError() {
    Instant originStartAt = Instant.parse("2026-08-21T05:00:00Z");
    when(getRecurrenceOccurrence.get(1L, 20L, originStartAt))
        .thenThrow(new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND));

    assertThatThrownBy(() -> service().preview(1L, occurrenceDeletionRequest(20L, originStartAt)))
        .isInstanceOf(CalioException.class)
        .extracting(exception -> ((CalioException) exception).getErrorCode())
        .isEqualTo(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
  }

  @Test
  @DisplayName("전체 반복 일정 수정 Preview는 변경 전후 recurrence rule을 함께 반환한다")
  void givenSeriesRuleChange_whenPreview_thenIncludesBeforeAndAfterRules() {
    RecurrenceEventResponse existingSeries =
        new RecurrenceEventResponse(
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
            Instant.parse("2026-08-01T00:00:00Z"));
    when(getRecurrenceEvent.get(1L, 20L)).thenReturn(existingSeries);
    CalendarMutationToolRequest request =
        new CalendarMutationToolRequest(
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
            List.of("RRULE:FREQ=DAILY"));

    CalendarMutationPreview preview = service().preview(1L, request);

    assertThat(preview.scope()).isEqualTo(CalendarMutationScope.ENTIRE_SERIES);
    assertThat(preview.recurrence().before()).containsExactly("RRULE:FREQ=WEEKLY;BYDAY=FR");
    assertThat(preview.recurrence().after()).containsExactly("RRULE:FREQ=DAILY");
  }

  @Test
  @DisplayName("전체 반복 일정 수정에서 recurrence rule을 생략하면 기존 규칙을 유지한다")
  void givenOmittedSeriesRules_whenPreview_thenPreservesExistingRules() {
    RecurrenceEventResponse existingSeries = recurrenceSeries();
    when(getRecurrenceEvent.get(1L, 20L)).thenReturn(existingSeries);
    CalendarMutationToolRequest request = seriesUpdateRequest(null);

    CalendarMutationPreview preview = service().preview(1L, request);

    assertThat(preview.recurrence().after()).containsExactlyElementsOf(existingSeries.recurrence());
  }

  @Test
  @DisplayName("전체 반복 일정 수정에서 빈 recurrence rule은 Preview와 적용 모두 거절한다")
  void givenEmptySeriesRules_whenPreviewOrApply_thenRejectsValidationFailure() {
    when(getRecurrenceEvent.get(1L, 20L)).thenReturn(recurrenceSeries());
    CalendarMutationToolRequest request = seriesUpdateRequest(List.of());

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
        createEventUseCase,
        getEventUseCase,
        updateEventUseCase,
        deleteEventUseCase,
        createRecurrenceEvent,
        getRecurrenceEvent,
        getRecurrenceOccurrence,
        updateRecurrenceEvent,
        updateRecurrenceOccurrence,
        deleteRecurrenceEvent,
        deleteRecurrenceOccurrence,
        tagRepository,
        aiMutationPolicy);
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
        null);
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
        null);
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
        null);
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
        null);
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
        recurrenceRules);
  }

  private CalendarMutationToolRequest occurrenceDeletionRequest(
      Long recurrenceId, Instant originStartAt) {
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
        null);
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
        null);
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
        Instant.parse("2026-08-01T00:00:00Z"));
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
        Instant.parse("2026-08-01T00:00:00Z"));
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
        Instant.parse("2026-08-01T00:00:00Z"));
  }
}
