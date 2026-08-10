package com.calio.calendar.recurrence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceOccurrenceRequest;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class RecurrenceEventCommandServiceTest {

    @Mock
    private RecurrenceEventRepository recurrenceEventRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;

    @Mock
    private Rfc5545RecurrenceEngine recurrenceEngine;

    @InjectMocks
    private RecurrenceEventCommandService recurrenceEventCommandService;

    @Test
    @DisplayName("CommandService의 모든 상태 변경은 트랜잭션 경계 안에서 실행한다")
    void commandServiceUsesTransactionBoundary() {
        // when
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(
                RecurrenceEventCommandService.class,
                Transactional.class
        );

        // then
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    @Test
    @DisplayName("occurrence 수정은 기존 override를 직접 조회해 활성 상태로 전환하고 저장한다")
    void givenExistingOverride_whenUpdateOccurrence_thenActivatesAndSavesExactOverride() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent();
        Instant originStartAt = Instant.parse("2027-01-01T00:00:00Z");
        RecurrenceEventOverride existingOverride = RecurrenceEventOverride.deleted(
                recurrenceEvent,
                originStartAt,
                Instant.parse("2027-01-02T00:00:00Z")
        );
        UpdateRecurrenceOccurrenceRequest request = updateRequest(originStartAt);
        CanonicalSchedule schedule = schedule(request);
        when(recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(10L, originStartAt))
                .thenReturn(Optional.of(existingOverride));
        when(recurrenceEventOverrideRepository.saveAndFlush(existingOverride)).thenReturn(existingOverride);

        // when
        RecurrenceEventOverride result = recurrenceEventCommandService.updateRecurrenceOccurrence(
                recurrenceEvent,
                request,
                schedule
        );

        // then
        InOrder order = inOrder(recurrenceEventOverrideRepository);
        order.verify(recurrenceEventOverrideRepository)
                .findByRecurrenceEvent_IdAndOriginStartAt(10L, originStartAt);
        order.verify(recurrenceEventOverrideRepository).saveAndFlush(existingOverride);
        verify(recurrenceEngine, never()).containsOrigin(any(), any(), any());
        assertThat(result).isSameAs(existingOverride);
        assertThat(existingOverride.isDeleted()).isFalse();
        assertThat(existingOverride.getOverrideTitle()).isEqualTo("Updated occurrence");
    }

    @Test
    @DisplayName("기존 override가 없으면 현재 recurrence 회차인지 확인한 뒤 활성 override를 생성한다")
    void givenMissingOverrideForCurrentOrigin_whenUpdateOccurrence_thenCreatesActiveOverride() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent();
        Instant originStartAt = Instant.parse("2027-01-01T00:00:00Z");
        UpdateRecurrenceOccurrenceRequest request = updateRequest(originStartAt);
        CanonicalSchedule schedule = schedule(request);
        when(recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(10L, originStartAt))
                .thenReturn(Optional.empty());
        when(recurrenceEngine.containsOrigin(any(), any(), any())).thenReturn(true);
        when(recurrenceEventOverrideRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        RecurrenceEventOverride result = recurrenceEventCommandService.updateRecurrenceOccurrence(
                recurrenceEvent,
                request,
                schedule
        );

        // then
        verify(recurrenceEventOverrideRepository).saveAndFlush(result);
        assertThat(result.getRecurrenceEvent()).isSameAs(recurrenceEvent);
        assertThat(result.getOriginStartAt()).isEqualTo(originStartAt);
        assertThat(result.getOverrideTitle()).isEqualTo("Updated occurrence");
        assertThat(result.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("기존 override와 현재 recurrence 회차가 모두 없으면 상태를 생성하지 않는다")
    void givenMissingOverrideForUnknownOrigin_whenUpdateOccurrence_thenRejectsWithoutSaving() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent();
        Instant originStartAt = Instant.parse("2027-01-01T00:00:01Z");
        UpdateRecurrenceOccurrenceRequest request = updateRequest(originStartAt);
        when(recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(10L, originStartAt))
                .thenReturn(Optional.empty());
        when(recurrenceEngine.containsOrigin(any(), any(), any())).thenReturn(false);

        // when, then
        assertThatThrownBy(() -> recurrenceEventCommandService.updateRecurrenceOccurrence(
                recurrenceEvent,
                request,
                schedule(request)
        ))
                .isInstanceOf(CalioException.class)
                .extracting(exception -> ((CalioException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
        verify(recurrenceEventOverrideRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("occurrence 삭제 command는 전달받은 override를 변경하지 않고 저장한다")
    void givenDeletedOverride_whenDeleteOccurrence_thenOnlySavesExactOverride() {
        // given
        RecurrenceEventOverride override = RecurrenceEventOverride.deleted(
                recurrenceEvent(),
                Instant.parse("2027-01-01T00:00:00Z"),
                Instant.parse("2027-01-02T00:00:00Z")
        );

        // when
        recurrenceEventCommandService.deleteRecurrenceOccurrence(override);

        // then
        verify(recurrenceEventOverrideRepository).saveAndFlush(override);
        assertThat(override.isDeleted()).isTrue();
    }

    private UpdateRecurrenceOccurrenceRequest updateRequest(Instant originStartAt) {
        return new UpdateRecurrenceOccurrenceRequest(
                originStartAt,
                "Updated occurrence",
                null,
                Instant.parse("2027-01-03T02:00:00Z"),
                Instant.parse("2027-01-03T03:00:00Z"),
                false,
                "Asia/Seoul"
        );
    }

    private CanonicalSchedule schedule(UpdateRecurrenceOccurrenceRequest request) {
        return CanonicalSchedule.recurrenceOverride(
                request.startAt(),
                request.endAt(),
                request.allDay(),
                request.timeZone()
        );
    }

    private RecurrenceEvent recurrenceEvent() {
        RecurrenceEvent recurrenceEvent = new RecurrenceEvent(
                "Rule",
                "memo",
                RecurrenceSchedule.create(
                        false,
                        Instant.parse("2027-01-01T00:00:00Z"),
                        Instant.parse("2027-01-01T01:00:00Z"),
                        "Asia/Seoul"
                ),
                List.of("RRULE:FREQ=DAILY;COUNT=3"),
                new Tag(TagType.DEFAULT, "기타", "#64748B"),
                new Account()
        );
        ReflectionTestUtils.setField(recurrenceEvent, "id", 10L);
        return recurrenceEvent;
    }
}
