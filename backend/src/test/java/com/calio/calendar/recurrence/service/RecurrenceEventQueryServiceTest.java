package com.calio.calendar.recurrence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class RecurrenceEventQueryServiceTest {

    @Mock
    private RecurrenceEventRepository recurrenceEventRepository;

    @Mock
    private RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;

    @InjectMocks
    private RecurrenceEventQueryService recurrenceEventQueryService;

    @Test
    @DisplayName("QueryService의 모든 조회는 readOnly 트랜잭션 경계 안에서 실행한다")
    void queryServiceUsesReadOnlyTransactionBoundary() {
        // when
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(
                RecurrenceEventQueryService.class,
                Transactional.class
        );

        // then
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    @DisplayName("계정 소유 반복 일정은 DTO 변환 없이 domain entity로 조회한다")
    void givenOwnedRecurrenceEvent_whenGet_thenReturnsEntity() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent();
        when(recurrenceEventRepository.findByIdAndAccount_Id(10L, 1L))
                .thenReturn(Optional.of(recurrenceEvent));

        // when
        RecurrenceEvent result = recurrenceEventQueryService.getRecurrenceEvent(1L, 10L);

        // then
        assertThat(result).isSameAs(recurrenceEvent);
    }

    @Test
    @DisplayName("계정 소유 반복 일정이 없으면 RECURRENCE_EVENT_NOT_FOUND를 반환한다")
    void givenMissingRecurrenceEvent_whenGet_thenThrowsNotFound() {
        // given
        when(recurrenceEventRepository.findByIdAndAccount_Id(10L, 1L))
                .thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> recurrenceEventQueryService.getRecurrenceEvent(1L, 10L))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RECURRENCE_EVENT_NOT_FOUND)
                );
    }

    @Test
    @DisplayName("반복 일정 확장 후보 조회는 계정과 종료 시각을 repository에 그대로 위임한다")
    void givenExpansionRange_whenFindCandidates_thenReturnsRepositoryResult() {
        // given
        Instant to = Instant.parse("2027-01-02T00:00:00Z");
        List<RecurrenceEvent> recurrenceEvents = List.of(recurrenceEvent());
        when(recurrenceEventRepository.findExpansionCandidatesStartedBefore(1L, to))
                .thenReturn(recurrenceEvents);

        // when
        List<RecurrenceEvent> result = recurrenceEventQueryService
                .findExpansionCandidatesStartedBefore(1L, to);

        // then
        assertThat(result).isSameAs(recurrenceEvents);
        verify(recurrenceEventRepository).findExpansionCandidatesStartedBefore(1L, to);
    }

    @Test
    @DisplayName("반복 회차 override 조회는 반복 일정과 origin 목록을 repository에 그대로 위임한다")
    void givenOccurrenceOrigins_whenFindOverrides_thenReturnsRepositoryResult() {
        // given
        List<Instant> originStartAts = List.of(Instant.parse("2027-01-01T00:00:00Z"));
        List<RecurrenceEventOverride> overrides = List.of(mock(RecurrenceEventOverride.class));
        when(recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAtIn(
                10L,
                originStartAts
        )).thenReturn(overrides);

        // when
        List<RecurrenceEventOverride> result = recurrenceEventQueryService
                .findOverrides(10L, originStartAts);

        // then
        assertThat(result).isSameAs(overrides);
        verify(recurrenceEventOverrideRepository)
                .findByRecurrenceEvent_IdAndOriginStartAtIn(10L, originStartAts);
    }

    @Test
    @DisplayName("범위에 이동해 들어온 활성 override 조회는 조회 범위를 repository에 그대로 위임한다")
    void givenTimeRange_whenFindActiveOverrides_thenReturnsRepositoryResult() {
        // given
        Instant from = Instant.parse("2027-01-01T00:00:00Z");
        Instant to = Instant.parse("2027-01-02T00:00:00Z");
        List<RecurrenceEventOverride> overrides = List.of(mock(RecurrenceEventOverride.class));
        when(recurrenceEventOverrideRepository.findActiveOverlappingOverrides(1L, from, to))
                .thenReturn(overrides);

        // when
        List<RecurrenceEventOverride> result = recurrenceEventQueryService
                .findActiveOverlappingOverrides(1L, from, to);

        // then
        assertThat(result).isSameAs(overrides);
        verify(recurrenceEventOverrideRepository).findActiveOverlappingOverrides(1L, from, to);
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
