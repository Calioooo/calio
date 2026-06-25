package com.calio.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.calio.calendar.controller.dto.EventResponse;
import com.calio.calendar.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.controller.dto.UpdateRecurrenceOccurrenceRequest;
import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import com.calio.calendar.repository.EventRepository;
import com.calio.calendar.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.repository.RecurrenceEventRepository;
import com.calio.calendar.repository.entity.Event;
import com.calio.calendar.repository.entity.RecurrenceEventOverride;
import com.calio.calendar.repository.entity.RecurrenceEvent;
import com.calio.calendar.repository.entity.RecurrenceFrequency;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecurrenceEventServiceTest {

    @Mock
    private RecurrenceEventRepository recurrenceEventRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;

    @InjectMocks
    private RecurrenceEventService recurrenceEventService;

    @Test
    @DisplayName("반복 일정 전체 수정은 non-null 요청 필드만 병합하고 null 필드는 기존 값을 보존한다")
    void givenNullFields_whenUpdateRecurrenceEvent_thenPreservesExistingValues() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent(
                "Original",
                "Keep me",
                "2026-11-01",
                "2026-11-02",
                RecurrenceFrequency.DAILY
        );
        when(recurrenceEventRepository.findById(1L)).thenReturn(Optional.of(recurrenceEvent));
        when(eventRepository.findByRecurrenceIdOrderByStartAtAsc(1L)).thenReturn(List.of());

        UpdateRecurrenceEventRequest request = new UpdateRecurrenceEventRequest(
                "Updated",
                null,
                null,
                null,
                RecurrenceFrequency.WEEKLY
        );

        // when
        recurrenceEventService.updateRecurrenceEvent(1L, request);

        // then
        assertThat(recurrenceEvent.getRecurrenceTitle()).isEqualTo("Updated");
        assertThat(recurrenceEvent.getRecurrenceDescription()).isEqualTo("Keep me");
        assertThat(recurrenceEvent.getRecurrenceStartDate()).isEqualTo(LocalDate.parse("2026-11-01"));
        assertThat(recurrenceEvent.getRecurrenceEndDate()).isEqualTo(LocalDate.parse("2026-11-02"));
        assertThat(recurrenceEvent.getRecurrenceFrequency()).isEqualTo(RecurrenceFrequency.WEEKLY);
    }

    @Test
    @DisplayName("반복 일정 전체 수정은 병합 후 effective time range가 유효하지 않으면 저장하지 않는다")
    void givenInvalidMergedTimeRange_whenUpdateRecurrenceEvent_thenThrowsRecurrenceUpdateTimeRangeInvalid() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent(
                "Original",
                null,
                "2026-12-01",
                "2026-12-01",
                RecurrenceFrequency.DAILY
        );
        when(recurrenceEventRepository.findById(1L)).thenReturn(Optional.of(recurrenceEvent));

        UpdateRecurrenceEventRequest request = new UpdateRecurrenceEventRequest(
                null,
                null,
                Instant.parse("2026-12-01T10:00:00Z"),
                Instant.parse("2026-12-01T10:00:00Z"),
                null
        );

        // when, then
        assertThatThrownBy(() -> recurrenceEventService.updateRecurrenceEvent(1L, request))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RECURRENCE_UPDATE_TIME_RANGE_INVALID)
                );
    }

    @Test
    @DisplayName("반복 일정 전체 수정은 effective instant range가 유효하면 종료 시각이 시작 시각보다 이른 값도 허용한다")
    void givenValidEffectiveInstantRange_whenUpdateRecurrenceEvent_thenAllowsEndTimeBeforeStartTime() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent(
                "Original",
                null,
                "2026-12-01",
                "2026-12-02",
                RecurrenceFrequency.DAILY
        );
        when(recurrenceEventRepository.findById(1L)).thenReturn(Optional.of(recurrenceEvent));
        when(eventRepository.findByRecurrenceIdOrderByStartAtAsc(1L)).thenReturn(List.of());

        UpdateRecurrenceEventRequest request = new UpdateRecurrenceEventRequest(
                null,
                null,
                Instant.parse("2026-12-01T23:00:00Z"),
                Instant.parse("2026-12-02T01:00:00Z"),
                null
        );

        // when
        recurrenceEventService.updateRecurrenceEvent(1L, request);

        // then
        assertThat(recurrenceEvent.getRecurrenceStartTime()).isEqualTo(LocalTime.parse("23:00:00"));
        assertThat(recurrenceEvent.getRecurrenceEndTime()).isEqualTo(LocalTime.parse("01:00:00"));
    }

    @Test
    @DisplayName("반복 일정 전체 수정은 존재하지 않는 recurrenceId면 RECURRENCE_EVENT_NOT_FOUND를 반환한다")
    void givenMissingRecurrenceId_whenUpdateRecurrenceEvent_thenThrowsRecurrenceEventNotFound() {
        // given
        when(recurrenceEventRepository.findById(999L)).thenReturn(Optional.empty());
        UpdateRecurrenceEventRequest request = new UpdateRecurrenceEventRequest(null, null, null, null, null);

        // when, then
        assertThatThrownBy(() -> recurrenceEventService.updateRecurrenceEvent(999L, request))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RECURRENCE_EVENT_NOT_FOUND)
                );
    }

    @Test
    @DisplayName("반복 일정의 단일 occurrence 수정 시 null이 아닌 필드만 업데이트하고 recurrenceId는 변경하지 않는다")
    void givenPartialFields_whenUpdateRecurrenceOccurrence_thenUpdatesEventAndPreservesRecurrenceId() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent(
                "Rule",
                "Rule memo",
                "2027-02-01",
                "2027-02-03",
                RecurrenceFrequency.DAILY
        );
        Event event = event("Original occurrence", "2027-02-01T09:00:00Z", "2027-02-01T10:00:00Z", 1L);
        ReflectionTestUtils.setField(event, "id", 10L);

        when(recurrenceEventRepository.findById(1L)).thenReturn(Optional.of(recurrenceEvent));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(recurrenceEventOverrideRepository.findByEventId(10L)).thenReturn(Optional.empty());
        when(recurrenceEventOverrideRepository.save(any(RecurrenceEventOverride.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateRecurrenceOccurrenceRequest request = new UpdateRecurrenceOccurrenceRequest(
                "Updated occurrence",
                null,
                Instant.parse("2027-02-01T09:30:00Z"),
                null,
                true
        );

        // when
        EventResponse response = recurrenceEventService.updateRecurrenceOccurrence(1L, 10L, request);

        // then
        assertThat(response.title()).isEqualTo("Updated occurrence");
        assertThat(response.description()).isNull();
        assertThat(response.startAt()).isEqualTo(Instant.parse("2027-02-01T09:30:00Z"));
        assertThat(response.endAt()).isEqualTo(Instant.parse("2027-02-01T10:00:00Z"));
        assertThat(response.importantEvent()).isTrue();
        assertThat(response.recurrenceId()).isEqualTo(1L);
        assertThat(event.getRecurrenceId()).contains(1L);
    }

    @Test
    @DisplayName("반복 occurrence 수정은 eventId가 path recurrenceId에 속하지 않으면 RECURRENCE_OCCURRENCE_NOT_FOUND를 반환한다")
    void givenMismatchedRecurrenceId_whenUpdateRecurrenceOccurrence_thenThrowsOccurrenceNotFound() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent(
                "Rule",
                null,
                "2027-03-01",
                "2027-03-02",
                RecurrenceFrequency.DAILY
        );
        Event event = event("Other occurrence", "2027-03-01T09:00:00Z", "2027-03-01T10:00:00Z", 2L);

        when(recurrenceEventRepository.findById(1L)).thenReturn(Optional.of(recurrenceEvent));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

        UpdateRecurrenceOccurrenceRequest request = new UpdateRecurrenceOccurrenceRequest(null, null, null, null, null);

        // when, then
        assertThatThrownBy(() -> recurrenceEventService.updateRecurrenceOccurrence(1L, 10L, request))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND)
                );
    }

    @Test
    @DisplayName("반복 일정의 단일 occurrence 수정에서 occurrence가 아닌 일반 evnet를 수정하려고 하면 RECURRENCE_OCCURRENCE_NOT_FOUND를 반환한다.")
    void givenNormalEvent_whenUpdateRecurrenceOccurrence_thenThrowsOccurrenceNotFound() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent(
                "Rule",
                null,
                "2027-03-11",
                "2027-03-12",
                RecurrenceFrequency.DAILY
        );
        Event event = event("Normal event", "2027-03-11T09:00:00Z", "2027-03-11T10:00:00Z", null);

        when(recurrenceEventRepository.findById(1L)).thenReturn(Optional.of(recurrenceEvent));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

        UpdateRecurrenceOccurrenceRequest request = new UpdateRecurrenceOccurrenceRequest(null, null, null, null, null);

        // when, then
        assertThatThrownBy(() -> recurrenceEventService.updateRecurrenceOccurrence(1L, 10L, request))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND)
                );
    }

    @Test
    @DisplayName("반복 occurrence 수정은 존재하지 않는 eventId면 EVENT_NOT_FOUND를 반환한다")
    void givenMissingEventId_whenUpdateRecurrenceOccurrence_thenThrowsEventNotFound() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent(
                "Rule",
                null,
                "2027-04-01",
                "2027-04-02",
                RecurrenceFrequency.DAILY
        );
        when(recurrenceEventRepository.findById(1L)).thenReturn(Optional.of(recurrenceEvent));
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());

        UpdateRecurrenceOccurrenceRequest request = new UpdateRecurrenceOccurrenceRequest(null, null, null, null, null);

        // when, then
        assertThatThrownBy(() -> recurrenceEventService.updateRecurrenceOccurrence(1L, 999L, request))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EVENT_NOT_FOUND)
                );
    }

    @Test
    @DisplayName("사용자가 반복 일정의 단일 occurrence를 삭제하면 Event의 deletedAt만 설정되고 Override는 변경되지 않는다")
    void givenExistingOccurrence_whenDeleteRecurrenceOccurrence_thenSoftDeletesEventWithoutOverrideUse() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent(
                "Rule",
                null,
                "2027-04-11",
                "2027-04-11",
                RecurrenceFrequency.DAILY
        );
        Event event = event("Occurrence", "2027-04-11T09:00:00Z", "2027-04-11T10:00:00Z", 1L);
        String originalTitle = event.getTitle();
        Instant originalStartAt = event.getStartAt();
        Instant originalEndAt = event.getEndAt();

        when(recurrenceEventRepository.findById(1L)).thenReturn(Optional.of(recurrenceEvent));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

        // when
        recurrenceEventService.deleteRecurrenceOccurrence(1L, 10L);

        // then
        assertThat(event.getDeletedAt()).isNotNull();
        assertThat(event.getTitle()).isEqualTo(originalTitle);
        assertThat(event.getStartAt()).isEqualTo(originalStartAt);
        assertThat(event.getEndAt()).isEqualTo(originalEndAt);
        assertThat(event.getRecurrenceId()).contains(1L);
        verify(eventRepository).flush();
        verifyNoInteractions(recurrenceEventOverrideRepository);
    }

    @Test
    @DisplayName("반복 occurrence 삭제는 이미 deletedAt이 있으면 기존 값을 보존한다")
    void givenAlreadyDeletedOccurrence_whenDeleteRecurrenceOccurrence_thenPreservesDeletedAt() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent(
                "Rule",
                null,
                "2027-04-21",
                "2027-04-21",
                RecurrenceFrequency.DAILY
        );
        Event event = event("Occurrence", "2027-04-21T09:00:00Z", "2027-04-21T10:00:00Z", 1L);
        Instant existingDeletedAt = Instant.parse("2027-04-21T12:00:00Z");
        event.softDelete(existingDeletedAt);

        when(recurrenceEventRepository.findById(1L)).thenReturn(Optional.of(recurrenceEvent));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

        // when
        recurrenceEventService.deleteRecurrenceOccurrence(1L, 10L);

        // then
        assertThat(event.getDeletedAt()).isEqualTo(existingDeletedAt);
        verify(eventRepository).flush();
        verifyNoInteractions(recurrenceEventOverrideRepository);
    }

    @Test
    @DisplayName("반복 occurrence 삭제는 recurrenceId를 먼저 확인하고 없으면 event를 조회하지 않는다")
    void givenMissingRecurrenceId_whenDeleteRecurrenceOccurrence_thenThrowsRecurrenceEventNotFound() {
        // given
        when(recurrenceEventRepository.findById(999L)).thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> recurrenceEventService.deleteRecurrenceOccurrence(999L, 10L))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RECURRENCE_EVENT_NOT_FOUND)
                );
        verify(eventRepository, never()).findById(10L);
        verifyNoInteractions(recurrenceEventOverrideRepository);
    }

    @Test
    @DisplayName("반복 occurrence 삭제는 존재하지 않는 eventId면 EVENT_NOT_FOUND를 반환한다")
    void givenMissingEventId_whenDeleteRecurrenceOccurrence_thenThrowsEventNotFound() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent(
                "Rule",
                null,
                "2027-04-22",
                "2027-04-22",
                RecurrenceFrequency.DAILY
        );
        when(recurrenceEventRepository.findById(1L)).thenReturn(Optional.of(recurrenceEvent));
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> recurrenceEventService.deleteRecurrenceOccurrence(1L, 999L))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EVENT_NOT_FOUND)
                );
        verifyNoInteractions(recurrenceEventOverrideRepository);
    }

    @Test
    @DisplayName("반복 occurrence 삭제는 event가 path recurrenceId에 속하지 않으면 RECURRENCE_OCCURRENCE_NOT_FOUND를 반환한다")
    void givenMismatchedRecurrenceId_whenDeleteRecurrenceOccurrence_thenThrowsOccurrenceNotFound() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent(
                "Rule",
                null,
                "2027-04-23",
                "2027-04-23",
                RecurrenceFrequency.DAILY
        );
        Event event = event("Other occurrence", "2027-04-23T09:00:00Z", "2027-04-23T10:00:00Z", 2L);

        when(recurrenceEventRepository.findById(1L)).thenReturn(Optional.of(recurrenceEvent));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

        // when, then
        assertThatThrownBy(() -> recurrenceEventService.deleteRecurrenceOccurrence(1L, 10L))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND)
                );
        assertThat(event.getDeletedAt()).isNull();
        verifyNoInteractions(recurrenceEventOverrideRepository);
    }

    @Test
    @DisplayName("반복 occurrence 삭제는 일반 event면 RECURRENCE_OCCURRENCE_NOT_FOUND를 반환한다")
    void givenNormalEvent_whenDeleteRecurrenceOccurrence_thenThrowsOccurrenceNotFound() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent(
                "Rule",
                null,
                "2027-04-24",
                "2027-04-24",
                RecurrenceFrequency.DAILY
        );
        Event event = event("Normal event", "2027-04-24T09:00:00Z", "2027-04-24T10:00:00Z", null);

        when(recurrenceEventRepository.findById(1L)).thenReturn(Optional.of(recurrenceEvent));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

        // when, then
        assertThatThrownBy(() -> recurrenceEventService.deleteRecurrenceOccurrence(1L, 10L))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND)
                );
        assertThat(event.getDeletedAt()).isNull();
        verifyNoInteractions(recurrenceEventOverrideRepository);
    }

    @Test
    @DisplayName("override가 이미 생성된 경우 기존 override를 사용해 occurrence를 수정한다")
    void givenExistingOverride_whenUpdateRecurrenceOccurrence_thenReusesOverride() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent(
                "Rule",
                null,
                "2027-05-01",
                "2027-05-02",
                RecurrenceFrequency.DAILY
        );
        Event event = event("Occurrence", "2027-05-01T09:00:00Z", "2027-05-01T10:00:00Z", 1L);
        RecurrenceEventOverride override = new RecurrenceEventOverride(10L);

        when(recurrenceEventRepository.findById(1L)).thenReturn(Optional.of(recurrenceEvent));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(recurrenceEventOverrideRepository.findByEventId(10L)).thenReturn(Optional.of(override));

        UpdateRecurrenceOccurrenceRequest request = new UpdateRecurrenceOccurrenceRequest("Updated", null, null, null, null);

        // when
        recurrenceEventService.updateRecurrenceOccurrence(1L, 10L, request);

        // then
        verify(recurrenceEventOverrideRepository, never()).save(any(RecurrenceEventOverride.class));
    }

    @Test
    @DisplayName("반복 occurrence override 생성 경합은 DuplicateKeyException 이후 기존 override 조회로 흡수한다")
    void givenDuplicateOverrideRace_whenUpdateRecurrenceOccurrence_thenFindsExistingOverride() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent(
                "Rule",
                null,
                "2027-06-01",
                "2027-06-02",
                RecurrenceFrequency.DAILY
        );
        Event event = event("Occurrence", "2027-06-01T09:00:00Z", "2027-06-01T10:00:00Z", 1L);
        RecurrenceEventOverride override = new RecurrenceEventOverride(10L);

        when(recurrenceEventRepository.findById(1L)).thenReturn(Optional.of(recurrenceEvent));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(recurrenceEventOverrideRepository.findByEventId(10L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(override));
        when(recurrenceEventOverrideRepository.save(any(RecurrenceEventOverride.class)))
                .thenThrow(new DuplicateKeyException("duplicate event_id"));

        UpdateRecurrenceOccurrenceRequest request = new UpdateRecurrenceOccurrenceRequest("Updated", null, null, null, null);

        // when
        recurrenceEventService.updateRecurrenceOccurrence(1L, 10L, request);

        // then
        verify(recurrenceEventOverrideRepository).save(any(RecurrenceEventOverride.class));
        assertThat(event.getTitle()).isEqualTo("Updated");
    }

    @Test
    @DisplayName("반복 일정 전체 수정은 유지 occurrence를 갱신하고 제외 occurrence를 hard-delete하며 신규 occurrence를 생성한다")
    @SuppressWarnings("unchecked")
    void givenChangedRule_whenUpdateRecurrenceEvent_thenRebuildsOccurrenceRows() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent(
                "Original",
                "Original memo",
                "2027-01-01",
                "2027-01-03",
                RecurrenceFrequency.DAILY
        );
        Event retainedFirst = event("Old", "2027-01-01T09:00:00Z", "2027-01-01T10:00:00Z", 1L);
        Event retainedSecond = event("Old", "2027-01-02T09:00:00Z", "2027-01-02T10:00:00Z", 1L);
        Event stale = event("Stale", "2027-01-09T09:00:00Z", "2027-01-09T10:00:00Z", 1L);

        when(recurrenceEventRepository.findById(1L)).thenReturn(Optional.of(recurrenceEvent));
        when(eventRepository.findByRecurrenceIdOrderByStartAtAsc(1L))
                .thenReturn(List.of(retainedFirst, retainedSecond, stale));

        UpdateRecurrenceEventRequest request = new UpdateRecurrenceEventRequest(
                "Updated",
                "Updated memo",
                null,
                Instant.parse("2027-01-04T10:00:00Z"),
                null
        );

        // when
        recurrenceEventService.updateRecurrenceEvent(1L, request);

        // then
        ArgumentCaptor<Iterable<Event>> deleteCaptor = ArgumentCaptor.forClass(Iterable.class);
        ArgumentCaptor<Iterable<Event>> saveCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(eventRepository).deleteAll(deleteCaptor.capture());
        verify(eventRepository).saveAll(saveCaptor.capture());

        assertThat(deleteCaptor.getValue()).containsExactly(stale);
        assertThat(saveCaptor.getValue())
                .hasSize(4)
                .contains(retainedFirst, retainedSecond)
                .extracting(event -> event.getStartAt().toString())
                .containsExactly(
                        "2027-01-01T09:00:00Z",
                        "2027-01-02T09:00:00Z",
                        "2027-01-03T09:00:00Z",
                        "2027-01-04T09:00:00Z"
                );
        assertThat(retainedFirst.getTitle()).isEqualTo("Updated");
        assertThat(retainedFirst.getDescription()).isEqualTo("Updated memo");
    }

    private RecurrenceEvent recurrenceEvent(
            String title,
            String description,
            String startDate,
            String endDate,
            RecurrenceFrequency recurrenceFrequency
    ) {
        RecurrenceEvent recurrenceEvent = new RecurrenceEvent(
                title,
                description,
                LocalDate.parse(startDate),
                LocalDate.parse(endDate),
                LocalTime.parse("09:00:00"),
                LocalTime.parse("10:00:00"),
                recurrenceFrequency
        );
        ReflectionTestUtils.setField(recurrenceEvent, "id", 1L);
        return recurrenceEvent;
    }

    private Event event(String title, String startAt, String endAt, Long recurrenceId) {
        return new Event(title, null, Instant.parse(startAt), Instant.parse(endAt), recurrenceId);
    }
}
