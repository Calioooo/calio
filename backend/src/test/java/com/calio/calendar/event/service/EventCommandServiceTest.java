package com.calio.calendar.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.controller.dto.UpdateEventRequest;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import java.time.Instant;
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
class EventCommandServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventCommandService eventCommandService;

    @Test
    @DisplayName("CommandService의 모든 상태 변경은 트랜잭션 경계 안에서 실행한다")
    void commandServiceUsesTransactionBoundary() {
        // when
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(
                EventCommandService.class,
                Transactional.class
        );

        // then
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    @Test
    @DisplayName("일정 생성은 전달받은 Event를 저장하고 저장 결과를 반환한다")
    void givenEvent_whenCreateEvent_thenReturnsSavedEvent() {
        // given
        Event event = event();
        when(eventRepository.save(event)).thenReturn(event);

        // when
        Event savedEvent = eventCommandService.createEvent(event);

        // then
        verify(eventRepository).save(event);
        assertThat(savedEvent).isSameAs(event);
    }

    @Test
    @DisplayName("일정 잠금 조회는 계정과 일정 ID를 repository에 정확히 전달한다")
    void givenOwnedEvent_whenFindForUpdate_thenReturnsLockedEvent() {
        // given
        Event event = event();
        when(eventRepository.findByIdAndAccountIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(event));

        // when
        Event result = eventCommandService.findEventForUpdate(1L, 10L);

        // then
        assertThat(result).isSameAs(event);
        verify(eventRepository).findByIdAndAccountIdForUpdate(10L, 1L);
    }

    @Test
    @DisplayName("잠금 조회할 계정 소유 일정이 없으면 EVENT_NOT_FOUND를 반환한다")
    void givenMissingOwnedEvent_whenFindForUpdate_thenThrowsEventNotFound() {
        // given
        when(eventRepository.findByIdAndAccountIdForUpdate(10L, 1L))
                .thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> eventCommandService.findEventForUpdate(1L, 10L))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EVENT_NOT_FOUND)
                );
    }

    @Test
    @DisplayName("일정 수정은 canonical schedule과 태그로 상태를 교체하고 flush한다")
    void givenCanonicalUpdate_whenUpdateEvent_thenReplacesStateAndFlushes() {
        // given
        Event event = event();
        Tag updatedTag = new Tag(TagType.CUSTOM, "업무", "#112233", account());
        UpdateEventRequest request = new UpdateEventRequest(
                "Updated",
                null,
                Instant.parse("2027-01-02T00:00:00Z"),
                Instant.parse("2027-01-03T00:00:00Z"),
                true,
                null,
                30L
        );
        CanonicalSchedule schedule = CanonicalSchedule.event(
                request.startAt(),
                request.endAt(),
                request.allDay(),
                request.timeZone()
        );

        // when
        eventCommandService.updateEvent(event, request, schedule, updatedTag);

        // then
        assertThat(event.getTitle()).isEqualTo("Updated");
        assertThat(event.getDescription()).isNull();
        assertThat(event.getStartAt()).isEqualTo(request.startAt());
        assertThat(event.getEndAt()).isEqualTo(request.endAt());
        assertThat(event.isAllDay()).isTrue();
        assertThat(event.getTimeZone()).isNull();
        assertThat(event.getTag()).isSameAs(updatedTag);
        verify(eventRepository).flush();
    }

    @Test
    @DisplayName("중요 일정 변경은 Event 상태를 교체하고 flush한다")
    void givenImportantState_whenUpdateImportantEvent_thenChangesStateAndFlushes() {
        // given
        Event event = event();

        // when
        eventCommandService.updateImportantEvent(event, true);

        // then
        assertThat(event.importantEvent()).isTrue();
        verify(eventRepository).flush();
    }

    @Test
    @DisplayName("일정 삭제는 조회된 정확한 Event를 repository에 전달한다")
    void givenEvent_whenDeleteEvent_thenDeletesExactEvent() {
        // given
        Event event = event();

        // when
        eventCommandService.deleteEvent(event);

        // then
        verify(eventRepository).delete(event);
    }

    private Event event() {
        Event event = new Event(
                "Original",
                "memo",
                Instant.parse("2027-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T01:00:00Z"),
                false,
                "UTC",
                null,
                new Tag(TagType.DEFAULT, "기타", "#64748B"),
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
}
