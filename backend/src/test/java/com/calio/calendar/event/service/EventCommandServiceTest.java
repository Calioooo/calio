package com.calio.calendar.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.event.controller.dto.UpdateEventRequest;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EventCommandServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventCommandService eventCommandService;

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
