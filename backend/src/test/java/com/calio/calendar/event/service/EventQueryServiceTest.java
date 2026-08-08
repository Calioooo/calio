package com.calio.calendar.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EventQueryServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private RecurrenceEventRepository recurrenceEventRepository;

    @Mock
    private RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;

    @Mock
    private Rfc5545RecurrenceEngine recurrenceEngine;

    @Mock
    private GoogleCalendarEventMappingRepository googleCalendarEventMappingRepository;

    @InjectMocks
    private EventQueryService eventQueryService;

    @Test
    @DisplayName("계정 소유 일정이 없으면 EVENT_NOT_FOUND를 반환한다")
    void givenMissingOwnedEvent_whenFindEvent_thenThrowsEventNotFound() {
        // given
        when(eventRepository.findByIdAndAccount_Id(10L, 1L)).thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> eventQueryService.findEvent(1L, 10L))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EVENT_NOT_FOUND)
                );
    }

    @Test
    @DisplayName("일정 조회 범위가 비어 있거나 역전되면 repository 조회 전에 거절한다")
    void givenNonIncreasingRange_whenListEvents_thenRejectsBeforeQueries() {
        // given
        Instant boundary = Instant.parse("2027-01-01T00:00:00Z");

        // when, then
        assertInvalidTimeRange(boundary, boundary);
        assertInvalidTimeRange(boundary.plusSeconds(1), boundary);
        verifyNoInteractions(
                eventRepository,
                recurrenceEventRepository,
                recurrenceEventOverrideRepository,
                recurrenceEngine
        );
    }

    @Test
    @DisplayName("일정 조회 범위는 정확히 366일까지 허용한다")
    void givenRangeAtLimit_whenListEvents_thenQueriesEvents() {
        // given
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = from.plus(Duration.ofDays(366));
        when(eventRepository.findNormalEvents(1L, from, to)).thenReturn(List.of());
        when(recurrenceEventRepository.findExpansionCandidatesStartedBefore(1L, to)).thenReturn(List.of());

        // when
        List<EventResponse> responses = eventQueryService.listEvents(1L, from, to);

        // then
        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("일정 조회 범위가 366일을 조금이라도 초과하면 repository 조회 전에 거절한다")
    void givenRangeOverLimit_whenListEvents_thenRejectsBeforeQueries() {
        // given
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = from.plus(Duration.ofDays(366)).plusNanos(1);

        // when, then
        assertThatThrownBy(() -> eventQueryService.listEvents(1L, from, to))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EVENT_QUERY_RANGE_TOO_LARGE)
                );
        verifyNoInteractions(
                eventRepository,
                recurrenceEventRepository,
                recurrenceEventOverrideRepository,
                recurrenceEngine
        );
    }

    @Test
    @DisplayName("일반 일정과 최종 반복 회차는 override 상태를 반영해 중복 없이 시작 시각순으로 조회한다")
    void givenNormalEventsAndRecurrenceOverrides_whenListEvents_thenReturnsFinalOccurrencesExactlyOnce() {
        // given
        Instant from = Instant.parse("2027-01-01T00:00:00Z");
        Instant to = Instant.parse("2027-01-01T02:00:00Z");
        Event normalEvent = event(
                1L,
                "Normal",
                Instant.parse("2027-01-01T00:45:00Z"),
                Instant.parse("2027-01-01T01:00:00Z")
        );
        RecurrenceEvent recurrenceEvent = recurrenceEvent();

        Instant activeOrigin = Instant.parse("2027-01-01T00:00:00Z");
        Instant deletedOrigin = Instant.parse("2027-01-01T00:30:00Z");
        Instant movedOutOrigin = Instant.parse("2027-01-01T01:00:00Z");
        Instant baseOrigin = Instant.parse("2027-01-01T01:30:00Z");
        List<RecurrenceOccurrence> occurrences = List.of(
                occurrence(activeOrigin, "2027-01-01T00:00:00Z", "2027-01-01T00:20:00Z"),
                occurrence(deletedOrigin, "2027-01-01T00:30:00Z", "2027-01-01T00:50:00Z"),
                occurrence(movedOutOrigin, "2027-01-01T01:00:00Z", "2027-01-01T01:20:00Z"),
                occurrence(baseOrigin, "2027-01-01T01:30:00Z", "2027-01-01T01:50:00Z")
        );
        RecurrenceEventOverride activeOverride = activeOverride(
                recurrenceEvent,
                activeOrigin,
                "Active override",
                "2027-01-01T00:15:00Z",
                "2027-01-01T00:25:00Z"
        );
        RecurrenceEventOverride deletedOverride = RecurrenceEventOverride.deleted(
                recurrenceEvent,
                deletedOrigin,
                Instant.parse("2027-01-02T00:00:00Z")
        );
        RecurrenceEventOverride movedOutOverride = activeOverride(
                recurrenceEvent,
                movedOutOrigin,
                "Moved out",
                "2027-01-01T03:00:00Z",
                "2027-01-01T03:20:00Z"
        );
        RecurrenceEventOverride movedInOrphanOverride = activeOverride(
                recurrenceEvent,
                Instant.parse("2026-12-31T00:00:00Z"),
                "Moved in orphan",
                "2027-01-01T00:30:00Z",
                "2027-01-01T00:40:00Z"
        );
        when(eventRepository.findNormalEvents(1L, from, to)).thenReturn(List.of(normalEvent));
        when(recurrenceEventRepository.findExpansionCandidatesStartedBefore(1L, to))
                .thenReturn(List.of(recurrenceEvent));
        when(recurrenceEngine.expand(any(RecurrenceSchedule.class), anyList(), eq(from), eq(to)))
                .thenReturn(occurrences);
        when(recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAtIn(
                10L,
                List.of(activeOrigin, deletedOrigin, movedOutOrigin, baseOrigin)
        )).thenReturn(List.of(activeOverride, deletedOverride, movedOutOverride));
        when(recurrenceEventOverrideRepository.findActiveOverlappingOverrides(1L, from, to))
                .thenReturn(List.of(activeOverride, movedInOrphanOverride));

        // when
        List<EventResponse> responses = eventQueryService.listEvents(1L, from, to);

        // then
        assertThat(responses)
                .extracting(EventResponse::title)
                .containsExactly("Active override", "Moved in orphan", "Normal", "Recurrence");
        assertThat(responses)
                .extracting(EventResponse::startAt)
                .containsExactly(
                        Instant.parse("2027-01-01T00:15:00Z"),
                        Instant.parse("2027-01-01T00:30:00Z"),
                        Instant.parse("2027-01-01T00:45:00Z"),
                        Instant.parse("2027-01-01T01:30:00Z")
                );
        assertThat(responses)
                .extracting(EventResponse::originStartAt)
                .containsExactly(
                        activeOrigin,
                        Instant.parse("2026-12-31T00:00:00Z"),
                        null,
                        baseOrigin
                );
    }

    private void assertInvalidTimeRange(Instant from, Instant to) {
        assertThatThrownBy(() -> eventQueryService.listEvents(1L, from, to))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TIME_RANGE)
                );
    }

    private Event event(Long id, String title, Instant startAt, Instant endAt) {
        Event event = new Event(
                title,
                null,
                startAt,
                endAt,
                false,
                "UTC",
                null,
                tag(),
                account()
        );
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    private RecurrenceEvent recurrenceEvent() {
        RecurrenceEvent recurrenceEvent = new RecurrenceEvent(
                "Recurrence",
                null,
                RecurrenceSchedule.create(
                        false,
                        Instant.parse("2027-01-01T00:00:00Z"),
                        Instant.parse("2027-01-01T00:20:00Z"),
                        "UTC"
                ),
                List.of("RRULE:FREQ=MINUTELY;COUNT=4"),
                tag(),
                account()
        );
        ReflectionTestUtils.setField(recurrenceEvent, "id", 10L);
        return recurrenceEvent;
    }

    private RecurrenceOccurrence occurrence(Instant originStartAt, String startAt, String endAt) {
        return new RecurrenceOccurrence(
                originStartAt,
                Instant.parse(startAt),
                Instant.parse(endAt)
        );
    }

    private RecurrenceEventOverride activeOverride(
            RecurrenceEvent recurrenceEvent,
            Instant originStartAt,
            String title,
            String startAt,
            String endAt
    ) {
        return RecurrenceEventOverride.active(
                recurrenceEvent,
                originStartAt,
                title,
                null,
                CanonicalSchedule.recurrenceOverride(
                        Instant.parse(startAt),
                        Instant.parse(endAt),
                        false,
                        "UTC"
                )
        );
    }

    private Account account() {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", 1L);
        return account;
    }

    private Tag tag() {
        return new Tag(TagType.DEFAULT, "기타", "#64748B");
    }
}
