package com.calio.calendar.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.controller.dto.CreateEventRequest;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.controller.dto.UpdateEventRequest;
import com.calio.calendar.event.controller.dto.UpdateImportantEventRequest;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingQueryService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingCommandService;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.recurrence.service.RecurrenceEventQueryService;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.service.TagQueryService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventQueryService eventQueryService;

    @Mock
    private EventCommandService eventCommandService;

    @Mock
    private AccountQueryService accountQueryService;

    @Mock
    private TagQueryService tagQueryService;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private GoogleCalendarEventMappingQueryService eventMappingQueryService;

    @Mock
    private GoogleCalendarEventMappingCommandService eventMappingCommandService;

    @Mock
    private RecurrenceEventRepository recurrenceEventRepository;

    @Mock
    private RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;

    @Mock
    private Rfc5545RecurrenceEngine recurrenceEngine;

    @InjectMocks
    private EventService eventService;

    @Test
    @DisplayName("일정 생성은 canonical schedule 검증 후 계정과 태그를 결합한 Event를 저장한다")
    void givenValidRequest_whenCreateEvent_thenStoresCanonicalEvent() {
        // given
        Account account = account();
        Tag tag = tag("기타");
        CreateEventRequest request = new CreateEventRequest(
                "New event",
                "memo",
                Instant.parse("2027-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T01:00:00Z"),
                false,
                "Asia/Seoul",
                20L
        );
        when(accountQueryService.getAccount(1L)).thenReturn(account);
        when(tagQueryService.getTagOrDefault(1L, 20L)).thenReturn(tag);
        when(eventCommandService.createEvent(any(Event.class))).thenAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            ReflectionTestUtils.setField(event, "id", 10L);
            return event;
        });

        // when
        EventResponse response = eventService.createEvent(1L, request);

        // then
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventCommandService).createEvent(eventCaptor.capture());
        Event storedEvent = eventCaptor.getValue();
        assertThat(storedEvent.getTitle()).isEqualTo("New event");
        assertThat(storedEvent.getStartAt()).isEqualTo(request.startAt());
        assertThat(storedEvent.getEndAt()).isEqualTo(request.endAt());
        assertThat(storedEvent.getTimeZone()).isEqualTo("Asia/Seoul");
        assertThat(storedEvent.getTag()).isSameAs(tag);
        assertThat(storedEvent.getAccount()).isSameAs(account);
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.title()).isEqualTo("New event");
    }

    @Test
    @DisplayName("일정 수정은 소유권과 외부 매핑을 조회한 뒤 태그를 해석하고 Command를 실행한다")
    void givenOwnedInternalEvent_whenUpdateEvent_thenQueriesPolicyBeforeCommand() {
        // given
        Event event = event("Before", tag("기존"));
        Tag updatedTag = tag("변경");
        UpdateEventRequest request = new UpdateEventRequest(
                "After",
                null,
                Instant.parse("2027-01-02T00:00:00Z"),
                Instant.parse("2027-01-02T02:00:00Z"),
                false,
                "UTC",
                30L
        );
        when(eventCommandService.lockEvent(1L, 10L)).thenReturn(event);
        when(eventMappingQueryService.blocksLocalMutation(10L)).thenReturn(false);
        when(tagQueryService.getTagOrDefault(1L, 30L)).thenReturn(updatedTag);
        doAnswer(invocation -> {
            Event target = invocation.getArgument(0);
            UpdateEventRequest updateRequest = invocation.getArgument(1);
            CanonicalSchedule schedule = invocation.getArgument(2);
            Tag tag = invocation.getArgument(3);
            target.replace(
                    updateRequest.title(),
                    updateRequest.description(),
                    schedule.startAt(),
                    schedule.endAt(),
                    schedule.allDay(),
                    schedule.timeZone(),
                    tag
            );
            return null;
        }).when(eventCommandService).updateEvent(any(), any(), any(), any());

        // when
        EventResponse response = eventService.updateEvent(1L, 10L, request);

        // then
        InOrder order = inOrder(eventMappingQueryService, tagQueryService, eventCommandService);
        order.verify(eventCommandService).lockEvent(1L, 10L);
        order.verify(eventMappingQueryService).blocksLocalMutation(10L);
        order.verify(tagQueryService).getTagOrDefault(1L, 30L);
        order.verify(eventCommandService).updateEvent(any(), any(), any(), any());
        assertThat(response.title()).isEqualTo("After");
        assertThat(response.startAt()).isEqualTo(request.startAt());
        assertThat(response.tag().title()).isEqualTo("변경");
    }

    @Test
    @DisplayName("외부 캘린더 일정 수정은 태그 조회와 상태 변경 없이 거절한다")
    void givenExternalEvent_whenUpdateEvent_thenRejectsBeforeMutation() {
        // given
        Event event = event("External", tag("기타"));
        when(eventCommandService.lockEvent(1L, 10L)).thenReturn(event);
        when(eventMappingQueryService.blocksLocalMutation(10L)).thenReturn(true);
        UpdateEventRequest request = new UpdateEventRequest(
                "Blocked",
                null,
                Instant.parse("2027-01-02T00:00:00Z"),
                Instant.parse("2027-01-02T01:00:00Z"),
                false,
                "UTC",
                null
        );

        // when, then
        assertThatThrownBy(() -> eventService.updateEvent(1L, 10L, request))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.EXTERNAL_EVENT_MUTATION_NOT_SUPPORTED)
                );
        verifyNoInteractions(tagQueryService);
        verify(eventCommandService, never()).updateEvent(any(), any(), any(), any());
        assertThat(event.getTitle()).isEqualTo("External");
    }

    @Test
    @DisplayName("연결 해제된 Google mapping 일정 수정은 local-only로 처리한다")
    void givenDisconnectedMappedEvent_whenUpdateEvent_thenUpdatesCanonicalEventLocally() {
        // given
        Event event = event("Before", tag("기타"));
        GoogleCalendarEventMapping mapping = org.mockito.Mockito.mock(GoogleCalendarEventMapping.class);
        UpdateEventRequest request = new UpdateEventRequest(
                "After",
                null,
                Instant.parse("2027-01-02T00:00:00Z"),
                Instant.parse("2027-01-02T01:00:00Z"),
                false,
                "UTC",
                null
        );
        when(eventCommandService.lockEvent(1L, 10L)).thenReturn(event);
        when(eventMappingQueryService.blocksLocalMutation(10L)).thenReturn(false);
        when(eventMappingQueryService.findEventMapping(10L)).thenReturn(Optional.of(mapping));
        when(mapping.blocksLocalMutation()).thenReturn(false);
        when(tagQueryService.getTagOrDefault(1L, null)).thenReturn(tag("기타"));

        // when
        eventService.updateEvent(1L, 10L, request);

        // then
        verify(eventCommandService).updateEvent(eq(event), eq(request), any(), any());
        verify(eventMappingCommandService).markLocalModification(eq(mapping), any(Instant.class));
    }

    @Test
    @DisplayName("외부 캘린더 일정 삭제는 Command를 실행하지 않고 거절한다")
    void givenExternalEvent_whenDeleteEvent_thenRejectsBeforeCommand() {
        // given
        Event event = event("External", tag("기타"));
        GoogleCalendarEventMapping mapping = org.mockito.Mockito.mock(GoogleCalendarEventMapping.class);
        GoogleCalendarIntegration integration = org.mockito.Mockito.mock(GoogleCalendarIntegration.class);
        when(eventCommandService.lockEvent(1L, 10L)).thenReturn(event);
        when(eventMappingQueryService.findEventMapping(10L)).thenReturn(Optional.of(mapping));
        when(mapping.getIntegration()).thenReturn(integration);
        when(integration.isConnected()).thenReturn(true);

        // when, then
        assertThatThrownBy(() -> eventService.deleteEvent(1L, 10L))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.EXTERNAL_EVENT_MUTATION_NOT_SUPPORTED)
                );
        verify(eventCommandService, never()).deleteEvent(any());
    }

    @Test
    @DisplayName("연결 해제된 Google mapping 일정 삭제는 mapping identity를 보존하고 canonical Event만 삭제한다")
    void givenDisconnectedMappedEvent_whenDeleteEvent_thenDetachesMappingAndDeletesEvent() {
        // given
        Event event = event("External", tag("기타"));
        GoogleCalendarEventMapping mapping = org.mockito.Mockito.mock(GoogleCalendarEventMapping.class);
        GoogleCalendarIntegration integration = org.mockito.Mockito.mock(GoogleCalendarIntegration.class);
        when(eventCommandService.lockEvent(1L, 10L)).thenReturn(event);
        when(eventMappingQueryService.findEventMapping(10L)).thenReturn(Optional.of(mapping));
        when(mapping.getIntegration()).thenReturn(integration);
        when(integration.isConnected()).thenReturn(false);

        // when
        eventService.deleteEvent(1L, 10L);

        // then
        verify(mapping).detachCanonicalEvent(any(Instant.class));
        verify(eventCommandService).deleteEvent(event);
    }

    @Test
    @DisplayName("외부 캘린더 일정의 중요 상태 변경은 Command를 실행하지 않고 거절한다")
    void givenExternalEvent_whenUpdateImportantEvent_thenRejectsBeforeCommand() {
        // given
        Event event = event("External", tag("기타"));
        when(eventCommandService.lockEvent(1L, 10L)).thenReturn(event);
        when(eventMappingQueryService.blocksLocalMutation(10L)).thenReturn(true);

        // when
        assertThatThrownBy(() -> eventService.updateImportantEvent(
                1L,
                10L,
                new UpdateImportantEventRequest(true)
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.EXTERNAL_EVENT_MUTATION_NOT_SUPPORTED)
        );

        // then
        verify(eventMappingQueryService).blocksLocalMutation(10L);
        verify(eventCommandService, never()).updateImportantEvent(any(), anyBoolean());
        assertThat(event.importantEvent()).isFalse();
    }

    @Test
    @DisplayName("일정 조회 범위가 비어 있거나 역전되면 repository 조회 전에 거절한다")
    void givenNonIncreasingRange_whenListEvents_thenRejectsBeforeQueries() {
        // given
        Instant boundary = Instant.parse("2027-01-01T00:00:00Z");
        EventService listEventService = listEventService();

        // when, then
        assertInvalidTimeRange(listEventService, boundary, boundary);
        assertInvalidTimeRange(listEventService, boundary.plusSeconds(1), boundary);
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
        List<EventResponse> responses = listEventService().listEvents(1L, from, to);

        // then
        assertThat(responses).isEmpty();
        verify(eventRepository).findNormalEvents(1L, from, to);
        verify(recurrenceEventRepository).findExpansionCandidatesStartedBefore(1L, to);
    }

    @Test
    @DisplayName("일정 조회 범위가 366일을 조금이라도 초과하면 repository 조회 전에 거절한다")
    void givenRangeOverLimit_whenListEvents_thenRejectsBeforeQueries() {
        // given
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = from.plus(Duration.ofDays(366)).plusNanos(1);

        // when, then
        assertThatThrownBy(() -> listEventService().listEvents(1L, from, to))
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
        List<EventResponse> responses = listEventService().listEvents(1L, from, to);

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

    private EventService listEventService() {
        EventQueryService queryService = new EventQueryService(eventRepository);
        RecurrenceEventQueryService recurrenceQueryService = new RecurrenceEventQueryService(
                recurrenceEventRepository,
                recurrenceEventOverrideRepository
        );
        return new EventService(
                queryService,
                eventCommandService,
                eventMappingQueryService,
                eventMappingCommandService,
                accountQueryService,
                tagQueryService,
                recurrenceQueryService,
                recurrenceEngine
        );
    }

    private void assertInvalidTimeRange(EventService service, Instant from, Instant to) {
        assertThatThrownBy(() -> service.listEvents(1L, from, to))
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
                tag("기타"),
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
                tag("기타"),
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

    private Event event(String title, Tag tag) {
        Event event = new Event(
                title,
                "memo",
                Instant.parse("2027-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T01:00:00Z"),
                false,
                "UTC",
                null,
                tag,
                account()
        );
        ReflectionTestUtils.setField(event, "id", 10L);
        return event;
    }

    private Account account() {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", 1L);
        return account;
    }

    private Tag tag(String title) {
        return new Tag(TagType.DEFAULT, title, "#64748B");
    }
}
