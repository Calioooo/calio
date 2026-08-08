package com.calio.calendar.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.controller.dto.CreateEventRequest;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.controller.dto.UpdateEventRequest;
import com.calio.calendar.event.controller.dto.UpdateImportantEventRequest;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.service.TagService;
import java.time.Instant;
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
class EventApplicationServiceTest {

    @Mock
    private EventQueryService eventQueryService;

    @Mock
    private EventCommandService eventCommandService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TagService tagService;

    @InjectMocks
    private EventApplicationService eventApplicationService;

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
        when(accountRepository.getReferenceById(1L)).thenReturn(account);
        when(tagService.getTagOrDefault(1L, 20L)).thenReturn(tag);
        when(eventCommandService.createEvent(any(Event.class))).thenAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            ReflectionTestUtils.setField(event, "id", 10L);
            return event;
        });

        // when
        EventResponse response = eventApplicationService.createEvent(1L, request);

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
        when(eventQueryService.findEvent(1L, 10L)).thenReturn(event);
        when(eventQueryService.hasExternalEventMapping(1L, 10L)).thenReturn(false);
        when(tagService.getTagOrDefault(1L, 30L)).thenReturn(updatedTag);
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
        EventResponse response = eventApplicationService.updateEvent(1L, 10L, request);

        // then
        InOrder order = inOrder(eventQueryService, tagService, eventCommandService);
        order.verify(eventQueryService).findEvent(1L, 10L);
        order.verify(eventQueryService).hasExternalEventMapping(1L, 10L);
        order.verify(tagService).getTagOrDefault(1L, 30L);
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
        when(eventQueryService.findEvent(1L, 10L)).thenReturn(event);
        when(eventQueryService.hasExternalEventMapping(1L, 10L)).thenReturn(true);
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
        assertThatThrownBy(() -> eventApplicationService.updateEvent(1L, 10L, request))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.EXTERNAL_EVENT_MUTATION_NOT_SUPPORTED)
                );
        verifyNoInteractions(tagService);
        verify(eventCommandService, never()).updateEvent(any(), any(), any(), any());
        assertThat(event.getTitle()).isEqualTo("External");
    }

    @Test
    @DisplayName("외부 캘린더 일정 삭제는 Command를 실행하지 않고 거절한다")
    void givenExternalEvent_whenDeleteEvent_thenRejectsBeforeCommand() {
        // given
        Event event = event("External", tag("기타"));
        when(eventQueryService.findEvent(1L, 10L)).thenReturn(event);
        when(eventQueryService.hasExternalEventMapping(1L, 10L)).thenReturn(true);

        // when, then
        assertThatThrownBy(() -> eventApplicationService.deleteEvent(1L, 10L))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.EXTERNAL_EVENT_MUTATION_NOT_SUPPORTED)
                );
        verify(eventCommandService, never()).deleteEvent(any());
    }

    @Test
    @DisplayName("중요 일정 PATCH는 외부 매핑 정책과 독립적으로 중요 상태를 변경한다")
    void givenExternalEvent_whenUpdateImportantEvent_thenAllowsImportantStateChange() {
        // given
        Event event = event("External", tag("기타"));
        when(eventQueryService.findEvent(1L, 10L)).thenReturn(event);
        doAnswer(invocation -> {
            Event target = invocation.getArgument(0);
            target.changeImportantEvent(invocation.getArgument(1));
            return null;
        }).when(eventCommandService).updateImportantEvent(event, true);

        // when
        EventResponse response = eventApplicationService.updateImportantEvent(
                1L,
                10L,
                new UpdateImportantEventRequest(true)
        );

        // then
        verify(eventQueryService, never()).hasExternalEventMapping(any(), any());
        verify(eventCommandService).updateImportantEvent(event, true);
        assertThat(response.importantEvent()).isTrue();
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
