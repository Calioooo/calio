package com.calio.calendar.recurrence.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService.OutboundOperation;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarRecurrenceJobKind;
import com.calio.calendar.recurrence.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceOccurrenceRequest;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.domain.Rfc5545RecurrenceEngine;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareRepository;
import com.calio.calendar.singleevent.controller.dto.EventResponse;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecurrenceEventUseCaseTest {

  @Mock private RecurrenceEventRepository recurrenceEventRepository;

  @Mock private AccountRepository accountRepository;

  @Mock private TagRepository tagRepository;

  @Mock private Rfc5545RecurrenceEngine recurrenceEngine;

  @Mock private Clock clock;

  @Mock private PersonalRecurrenceGroupShareRepository recurrenceShareRepository;

  @Mock private GoogleOperationJobEnqueueService jobEnqueueService;

  @Mock private OutboundOperation outboundOperation;

  private CreateRecurrenceEventUseCase createRecurrenceEvent;
  private GetRecurrenceEventUseCase getRecurrenceEvent;
  private GetRecurrenceOccurrenceUseCase getRecurrenceOccurrence;
  private UpdateRecurrenceEventUseCase updateRecurrenceEvent;
  private UpdateRecurrenceOccurrenceUseCase updateRecurrenceOccurrence;
  private DeleteRecurrenceEventUseCase deleteRecurrenceEvent;
  private DeleteRecurrenceOccurrenceUseCase deleteRecurrenceOccurrence;

  @BeforeEach
  void setUp() {
    lenient().when(jobEnqueueService.prepareOutboundOperation(1L)).thenReturn(outboundOperation);
    lenient().when(tagRepository.findById(2L)).thenReturn(Optional.of(tag()));
    createRecurrenceEvent =
        new CreateRecurrenceEventUseCase(
            accountRepository,
            tagRepository,
            recurrenceEventRepository,
            recurrenceEngine,
            jobEnqueueService);
    getRecurrenceEvent = new GetRecurrenceEventUseCase(recurrenceEventRepository, tagRepository);
    getRecurrenceOccurrence =
        new GetRecurrenceOccurrenceUseCase(
            recurrenceEventRepository, tagRepository, recurrenceEngine);
    updateRecurrenceEvent =
        new UpdateRecurrenceEventUseCase(
            recurrenceEventRepository, tagRepository, recurrenceEngine, jobEnqueueService);
    updateRecurrenceOccurrence =
        new UpdateRecurrenceOccurrenceUseCase(
            recurrenceEventRepository, tagRepository, recurrenceEngine, jobEnqueueService);
    deleteRecurrenceEvent =
        new DeleteRecurrenceEventUseCase(
            recurrenceEventRepository, recurrenceShareRepository, jobEnqueueService);
    deleteRecurrenceOccurrence =
        new DeleteRecurrenceOccurrenceUseCase(
            recurrenceEventRepository, recurrenceEngine, jobEnqueueService, clock);
  }

  @Test
  @DisplayName("반복 일정 생성은 정규화된 RFC line과 canonical schedule만 저장하고 SingleEvent row를 만들지 않는다")
  void givenTimedRequest_whenCreate_thenStoresValidatedMasterWithoutMaterializingEvents() {
    Tag tag = tag();
    List<String> normalized = List.of("RRULE:FREQ=DAILY;COUNT=3");
    when(tagRepository.findPersonalFallbackTag()).thenReturn(Optional.of(tag));
    when(accountRepository.existsById(1L)).thenReturn(true);
    when(recurrenceEngine.validate(any(RecurrenceSchedule.class), any())).thenReturn(normalized);
    when(recurrenceEventRepository.save(any(RecurrenceEvent.class)))
        .thenAnswer(
            invocation -> {
              RecurrenceEvent recurrenceEvent = invocation.getArgument(0);
              ReflectionTestUtils.setField(recurrenceEvent, "id", 10L);
              return recurrenceEvent;
            });
    CreateRecurrenceEventRequest request = timedCreateRequest();

    RecurrenceEventResponse response = createRecurrenceEvent.create(1L, request);

    ArgumentCaptor<RecurrenceEvent> captor = ArgumentCaptor.forClass(RecurrenceEvent.class);
    verify(recurrenceEventRepository).save(captor.capture());
    assertThat(captor.getValue().getFirstOccurrenceStartAt())
        .isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
    assertThat(captor.getValue().getFirstOccurrenceEndAt())
        .isEqualTo(Instant.parse("2027-01-01T01:00:00Z"));
    assertThat(captor.getValue().getTimeZone()).isEqualTo("Asia/Seoul");
    assertThat(captor.getValue().getRecurrenceRules()).containsExactlyElementsOf(normalized);
    assertThat(response.recurrenceId()).isEqualTo(10L);
    verify(jobEnqueueService)
        .enqueueRecurrence(
            eq(outboundOperation),
            eq(10L),
            eq(GoogleCalendarRecurrenceJobKind.RECURRENCE_CREATE),
            any());
  }

  @Test
  @DisplayName("계정이 소유한 반복 일정은 태그와 함께 응답 DTO로 변환한다")
  void givenOwnedRecurrenceEvent_whenGet_thenCreatesResponse() {
    RecurrenceEvent recurrenceEvent = recurrenceEvent();
    when(recurrenceEventRepository.findByIdAndAccountId(10L, 1L))
        .thenReturn(Optional.of(recurrenceEvent));

    RecurrenceEventResponse response = getRecurrenceEvent.get(1L, 10L);

    assertThat(response.recurrenceId()).isEqualTo(10L);
    assertThat(response.title()).isEqualTo("Rule");
    assertThat(response.description()).isEqualTo("memo");
    assertThat(response.recurrence()).containsExactly("RRULE:FREQ=DAILY;COUNT=3");
  }

  @Test
  @DisplayName("전체 수정은 새 정의 검증 후 master snapshot만 교체하고 기존 child 상태를 보존한다")
  void givenValidUpdate_whenUpdate_thenReplacesOnlyMaster() {
    RecurrenceEvent recurrenceEvent = recurrenceEvent();
    Tag tag = tag();
    List<String> normalized = List.of("RRULE:FREQ=WEEKLY;COUNT=2");
    when(recurrenceEventRepository.findByIdAndAccountIdForUpdate(10L, 1L))
        .thenReturn(Optional.of(recurrenceEvent));
    when(tagRepository.findPersonalFallbackTag()).thenReturn(Optional.of(tag));
    when(recurrenceEngine.validate(any(RecurrenceSchedule.class), any())).thenReturn(normalized);
    UpdateRecurrenceEventRequest request =
        new UpdateRecurrenceEventRequest(
            "Updated",
            null,
            true,
            Instant.parse("2027-02-01T00:00:00Z"),
            Instant.parse("2027-02-03T00:00:00Z"),
            null,
            normalized,
            null);

    updateRecurrenceEvent.update(1L, 10L, request);

    assertThat(recurrenceEvent.getTitle()).isEqualTo("Updated");
    assertThat(recurrenceEvent.isAllDay()).isTrue();
    assertThat(recurrenceEvent.getTimeZone()).isNull();
    InOrder lockOrder = inOrder(jobEnqueueService, recurrenceEventRepository);
    lockOrder.verify(jobEnqueueService).prepareOutboundOperation(1L);
    lockOrder.verify(recurrenceEventRepository).findByIdAndAccountIdForUpdate(10L, 1L);
    assertThat(recurrenceEvent.getOverrides()).isEmpty();
    verify(jobEnqueueService)
        .enqueueRecurrence(
            eq(outboundOperation),
            eq(10L),
            eq(GoogleCalendarRecurrenceJobKind.RECURRENCE_UPDATE),
            any());
  }

  @Test
  @DisplayName("전체 반복 일정 삭제는 공유를 정리한 뒤 Root의 생명주기로 삭제한다")
  void givenRecurrenceEvent_whenDelete_thenDeletesAggregateRoot() {
    RecurrenceEvent recurrenceEvent = recurrenceEvent();
    when(recurrenceEventRepository.findByIdAndAccountIdForUpdate(10L, 1L))
        .thenReturn(Optional.of(recurrenceEvent));

    deleteRecurrenceEvent.delete(1L, 10L);

    InOrder deletionOrder =
        inOrder(jobEnqueueService, recurrenceEventRepository, recurrenceShareRepository);
    deletionOrder.verify(jobEnqueueService).prepareOutboundOperation(1L);
    deletionOrder.verify(recurrenceEventRepository).findByIdAndAccountIdForUpdate(10L, 1L);
    deletionOrder.verify(recurrenceShareRepository).deleteAllByRecurrenceEventId(10L);
    deletionOrder.verify(recurrenceEventRepository).delete(recurrenceEvent);
    verify(jobEnqueueService).enqueueRecurrenceDeleted(outboundOperation, 10L);
  }

  @Test
  @DisplayName("occurrence PATCH는 title과 null description을 포함한 완전한 snapshot을 저장한다")
  void givenOccurrencePatch_whenUpdate_thenStoresCompleteSnapshot() {
    RecurrenceEvent recurrenceEvent = recurrenceEvent();
    Instant originStartAt = Instant.parse("2027-01-01T00:00:00Z");
    when(recurrenceEventRepository.findByIdAndAccountIdForUpdate(10L, 1L))
        .thenReturn(Optional.of(recurrenceEvent));
    when(recurrenceEngine.containsOrigin(any(), any(), any())).thenReturn(true);
    UpdateRecurrenceOccurrenceRequest request =
        new UpdateRecurrenceOccurrenceRequest(
            originStartAt,
            "Final title",
            null,
            Instant.parse("2027-01-03T02:00:00Z"),
            Instant.parse("2027-01-03T03:00:00Z"),
            false,
            "Asia/Seoul");

    EventResponse response = updateRecurrenceOccurrence.update(1L, 10L, request);

    RecurrenceEventOverride override = recurrenceEvent.findOverride(originStartAt).orElseThrow();
    verify(recurrenceEventRepository).flush();
    assertThat(override.getOverrideTitle()).isEqualTo("Final title");
    assertThat(override.getOverrideDescription()).isNull();
    assertThat(override.getOverrideTimeZone()).isEqualTo("Asia/Seoul");
    assertThat(response.title()).isEqualTo("Final title");
    assertThat(response.description()).isNull();
    assertThat(response.originStartAt()).isEqualTo(originStartAt);
    InOrder lockOrder = inOrder(jobEnqueueService, recurrenceEventRepository);
    lockOrder.verify(jobEnqueueService).prepareOutboundOperation(1L);
    lockOrder.verify(recurrenceEventRepository).findByIdAndAccountIdForUpdate(10L, 1L);
    verify(jobEnqueueService)
        .enqueueRecurrenceOverride(eq(outboundOperation), eq(10L), eq(originStartAt), any());
  }

  @Test
  @DisplayName("기존 override와 현재 recurrence 회차가 모두 없으면 PATCH 상태를 생성하지 않는다")
  void givenUnknownOriginWithoutOverride_whenUpdate_thenRejectsWithoutStateChange() {
    RecurrenceEvent recurrenceEvent = recurrenceEvent();
    Instant originStartAt = Instant.parse("2027-01-01T00:00:01Z");
    when(recurrenceEventRepository.findByIdAndAccountIdForUpdate(10L, 1L))
        .thenReturn(Optional.of(recurrenceEvent));
    when(recurrenceEngine.containsOrigin(any(), any(), any())).thenReturn(false);
    UpdateRecurrenceOccurrenceRequest request =
        new UpdateRecurrenceOccurrenceRequest(
            originStartAt,
            "Unknown occurrence",
            null,
            Instant.parse("2027-01-03T02:00:00Z"),
            Instant.parse("2027-01-03T03:00:00Z"),
            false,
            "Asia/Seoul");

    assertThatThrownBy(() -> updateRecurrenceOccurrence.update(1L, 10L, request))
        .isInstanceOf(CalioException.class)
        .extracting(exception -> ((CalioException) exception).getErrorCode())
        .isEqualTo(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
    assertThat(recurrenceEvent.findOverride(originStartAt)).isEmpty();
    verify(recurrenceEventRepository, never()).flush();
  }

  @Test
  @DisplayName("현재 규칙에서 사라진 제외 회차도 요청한 시간 형식의 변경 값으로 복원한다")
  void givenExcludedOrphanOccurrence_whenPatch_thenReplacesValueWithRequestedScheduleType() {
    RecurrenceEvent recurrenceEvent = recurrenceEvent();
    Instant originStartAt = Instant.parse("2027-01-01T00:00:00Z");
    RecurrenceEventOverride existingOverride =
        recurrenceEvent.changeOccurrence(
            originStartAt,
            "Old title",
            "old memo",
            CanonicalSchedule.recurrenceOverride(
                Instant.parse("2027-01-02T02:00:00Z"),
                Instant.parse("2027-01-02T03:00:00Z"),
                false,
                "Asia/Seoul"));
    recurrenceEvent.excludeOccurrence(originStartAt, Instant.parse("2027-01-05T00:00:00Z"));
    recurrenceEvent.update(
        "All day master",
        null,
        RecurrenceSchedule.create(
            true,
            Instant.parse("2027-02-01T00:00:00Z"),
            Instant.parse("2027-02-02T00:00:00Z"),
            null),
        List.of("RRULE:FREQ=WEEKLY;COUNT=2"),
        2L);
    when(recurrenceEventRepository.findByIdAndAccountIdForUpdate(10L, 1L))
        .thenReturn(Optional.of(recurrenceEvent));
    UpdateRecurrenceOccurrenceRequest request =
        new UpdateRecurrenceOccurrenceRequest(
            originStartAt,
            "Restored",
            null,
            Instant.parse("2027-03-01T00:00:00Z"),
            Instant.parse("2027-03-03T00:00:00Z"),
            true,
            null);

    updateRecurrenceOccurrence.update(1L, 10L, request);

    verify(recurrenceEngine, never()).containsOrigin(any(), any(), any());
    verify(recurrenceEventRepository).flush();
    RecurrenceEventOverride restored = recurrenceEvent.findOverride(originStartAt).orElseThrow();
    assertThat(restored).isNotSameAs(existingOverride);
    assertThat(existingOverride.getOverrideTitle()).isEqualTo("Old title");
    assertThat(restored.getOriginStartAt()).isEqualTo(originStartAt);
    assertThat(restored.getOverrideTitle()).isEqualTo("Restored");
    assertThat(restored.getOverrideStartAt()).isEqualTo(request.startAt());
    assertThat(restored.getOverrideEndAt()).isEqualTo(request.endAt());
    assertThat(restored.isOverrideAllDay()).isTrue();
    assertThat(restored.getOverrideTimeZone()).isNull();
    assertThat(restored.getDeletedAt()).isNull();
  }

  @Test
  @DisplayName("현재 rule에서 사라진 active override DELETE는 같은 회차의 값을 제외 상태로 교체한다")
  void givenActiveOrphanOccurrence_whenDelete_thenReplacesValueWithExclusion() {
    RecurrenceEvent recurrenceEvent = recurrenceEvent();
    Instant originStartAt = Instant.parse("2027-01-01T00:00:00Z");
    Instant deletedAt = Instant.parse("2027-01-06T00:00:00Z");
    RecurrenceEventOverride existingOverride =
        recurrenceEvent.changeOccurrence(
            originStartAt,
            "Override",
            null,
            CanonicalSchedule.recurrenceOverride(
                Instant.parse("2027-01-02T02:00:00Z"),
                Instant.parse("2027-01-02T03:00:00Z"),
                false,
                "Asia/Seoul"));
    when(recurrenceEventRepository.findByIdAndAccountIdForUpdate(10L, 1L))
        .thenReturn(Optional.of(recurrenceEvent));
    when(clock.instant()).thenReturn(deletedAt);

    deleteRecurrenceOccurrence.delete(1L, 10L, originStartAt);

    verify(recurrenceEngine, never()).containsOrigin(any(), any(), any());
    verify(recurrenceEventRepository).flush();
    RecurrenceEventOverride excluded = recurrenceEvent.findOverride(originStartAt).orElseThrow();
    assertThat(excluded).isNotSameAs(existingOverride);
    assertThat(existingOverride.isDeleted()).isFalse();
    assertThat(excluded.getOriginStartAt()).isEqualTo(originStartAt);
    assertThat(excluded.isDeleted()).isTrue();
    assertThat(excluded.getDeletedAt()).isEqualTo(deletedAt);
    InOrder lockOrder = inOrder(jobEnqueueService, recurrenceEventRepository);
    lockOrder.verify(jobEnqueueService).prepareOutboundOperation(1L);
    lockOrder.verify(recurrenceEventRepository).findByIdAndAccountIdForUpdate(10L, 1L);
    verify(jobEnqueueService)
        .enqueueRecurrenceOverrideDeleted(outboundOperation, 10L, originStartAt);
  }

  @Test
  @DisplayName("다른 날짜로 이동된 recurrence-occurrence는 origin identity로 현재 override를 조회한다")
  void
      givenMovedOccurrenceOverride_whenGetOccurrence_thenReturnsOverrideRegardlessOfOriginalRange() {
    RecurrenceEvent recurrenceEvent = recurrenceEvent();
    Instant originStartAt = Instant.parse("2027-01-01T00:00:00Z");
    RecurrenceEventOverride movedOverride =
        recurrenceEvent.changeOccurrence(
            originStartAt,
            "이동한 회의",
            "변경된 설명",
            CanonicalSchedule.recurrenceOverride(
                Instant.parse("2027-01-05T02:00:00Z"),
                Instant.parse("2027-01-05T03:00:00Z"),
                false,
                "Asia/Seoul"));
    when(recurrenceEventRepository.findByIdAndAccountId(10L, 1L))
        .thenReturn(Optional.of(recurrenceEvent));
    when(recurrenceEventRepository.findOverrideByRecurrenceIdAndOriginStartAt(10L, originStartAt))
        .thenReturn(Optional.of(movedOverride));

    EventResponse occurrence = getRecurrenceOccurrence.get(1L, 10L, originStartAt);

    assertThat(occurrence.title()).isEqualTo("이동한 회의");
    assertThat(occurrence.startAt()).isEqualTo(Instant.parse("2027-01-05T02:00:00Z"));
    assertThat(occurrence.originStartAt()).isEqualTo(originStartAt);
    verify(recurrenceEngine, never()).expand(any(), any(), any(), any());
  }

  @Test
  @DisplayName("현재 rule과 exact override에 없는 origin은 상태를 만들지 않고 거절한다")
  void givenUnknownOriginWithoutOverride_whenDelete_thenRejectsWithoutStateChange() {
    RecurrenceEvent recurrenceEvent = recurrenceEvent();
    Instant originStartAt = Instant.parse("2027-01-01T00:00:01Z");
    when(recurrenceEventRepository.findByIdAndAccountIdForUpdate(10L, 1L))
        .thenReturn(Optional.of(recurrenceEvent));
    when(recurrenceEngine.containsOrigin(any(), any(), any())).thenReturn(false);

    assertThatThrownBy(() -> deleteRecurrenceOccurrence.delete(1L, 10L, originStartAt))
        .isInstanceOf(CalioException.class)
        .extracting(exception -> ((CalioException) exception).getErrorCode())
        .isEqualTo(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
    assertThat(recurrenceEvent.findOverride(originStartAt)).isEmpty();
    verify(recurrenceEventRepository, never()).flush();
  }

  private CreateRecurrenceEventRequest timedCreateRequest() {
    return new CreateRecurrenceEventRequest(
        "Rule",
        "memo",
        false,
        Instant.parse("2027-01-01T00:00:00Z"),
        Instant.parse("2027-01-01T01:00:00Z"),
        "Asia/Seoul",
        List.of("RRULE:FREQ=DAILY;COUNT=3"),
        null);
  }

  private RecurrenceEvent recurrenceEvent() {
    RecurrenceEvent recurrenceEvent =
        new RecurrenceEvent(
            "Rule",
            "memo",
            RecurrenceSchedule.create(
                false,
                Instant.parse("2027-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T01:00:00Z"),
                "Asia/Seoul"),
            List.of("RRULE:FREQ=DAILY;COUNT=3"),
            2L,
            1L);
    ReflectionTestUtils.setField(recurrenceEvent, "id", 10L);
    return recurrenceEvent;
  }

  private Tag tag() {
    Tag tag = Tag.personalDefault("기타", "#64748B");
    ReflectionTestUtils.setField(tag, "id", 2L);
    return tag;
  }
}
