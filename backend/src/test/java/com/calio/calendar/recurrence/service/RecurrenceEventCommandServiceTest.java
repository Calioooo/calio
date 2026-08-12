package com.calio.calendar.recurrence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.repository.EventRepository;
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
    @DisplayName("반복 일정 잠금 조회는 계정과 반복 일정 ID를 repository에 정확히 전달한다")
    void givenOwnedRecurrenceEvent_whenFindForUpdate_thenReturnsLockedEvent() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent();
        when(recurrenceEventRepository.findByIdAndAccountIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(recurrenceEvent));

        // when
        RecurrenceEvent result = recurrenceEventCommandService.findRecurrenceEventForUpdate(1L, 10L);

        // then
        assertThat(result).isSameAs(recurrenceEvent);
        verify(recurrenceEventRepository).findByIdAndAccountIdForUpdate(10L, 1L);
    }

    @Test
    @DisplayName("잠금 조회할 계정 소유 반복 일정이 없으면 RECURRENCE_EVENT_NOT_FOUND를 반환한다")
    void givenMissingRecurrenceEvent_whenFindForUpdate_thenThrowsNotFound() {
        // given
        when(recurrenceEventRepository.findByIdAndAccountIdForUpdate(10L, 1L))
                .thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> recurrenceEventCommandService.findRecurrenceEventForUpdate(1L, 10L))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RECURRENCE_EVENT_NOT_FOUND)
                );
    }

    @Test
    @DisplayName("occurrence 수정 command는 전달받은 override를 변경하지 않고 저장한다")
    void givenActiveOverride_whenUpdateOccurrence_thenOnlySavesExactOverride() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent();
        Instant originStartAt = Instant.parse("2027-01-01T00:00:00Z");
        RecurrenceEventOverride override = RecurrenceEventOverride.active(
                recurrenceEvent,
                originStartAt,
                "Updated occurrence",
                null,
                CanonicalSchedule.recurrenceOverride(
                        Instant.parse("2027-01-03T02:00:00Z"),
                        Instant.parse("2027-01-03T03:00:00Z"),
                        false,
                        "Asia/Seoul"
                )
        );
        when(recurrenceEventOverrideRepository.saveAndFlush(override)).thenReturn(override);

        // when
        RecurrenceEventOverride result = recurrenceEventCommandService.updateRecurrenceOccurrence(override);

        // then
        verify(recurrenceEventOverrideRepository).saveAndFlush(override);
        assertThat(result).isSameAs(override);
        assertThat(override.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("반복 일정 삭제 command는 전달받은 ID의 자식 상태를 master보다 먼저 삭제한다")
    void givenRecurrenceId_whenDeleteRecurrenceEvent_thenDeletesChildrenBeforeMaster() {
        // when
        recurrenceEventCommandService.deleteRecurrenceEvent(10L);

        // then
        InOrder deletionOrder = inOrder(
                recurrenceEventOverrideRepository,
                eventRepository,
                recurrenceEventRepository
        );
        deletionOrder.verify(recurrenceEventOverrideRepository).deleteAllByRecurrenceEventIds(List.of(10L));
        deletionOrder.verify(eventRepository).deleteAllByRecurrenceEventIds(List.of(10L));
        deletionOrder.verify(recurrenceEventRepository).deleteAllByIds(List.of(10L));
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
