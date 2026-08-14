package com.calio.calendar.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
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
class EventQueryServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventQueryService eventQueryService;

    @Test
    @DisplayName("QueryService의 모든 조회는 readOnly 트랜잭션 경계 안에서 실행한다")
    void queryServiceUsesReadOnlyTransactionBoundary() {
        // when
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(
                EventQueryService.class,
                Transactional.class
        );

        // then
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    @DisplayName("계정 소유 일정이 없으면 EVENT_NOT_FOUND를 반환한다")
    void givenMissingOwnedEvent_whenFindEvent_thenThrowsEventNotFound() {
        // given
        when(eventRepository.findByIdAndAccount_Id(10L, 1L)).thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> eventQueryService.getEvent(1L, 10L))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EVENT_NOT_FOUND)
                );
    }

    @Test
    @DisplayName("일반 일정 조회는 범위 인수를 그대로 repository에 위임한다")
    void givenTimeRange_whenListEvents_thenReturnsRepositoryResult() {
        // given
        Instant from = Instant.parse("2027-01-01T00:00:00Z");
        Instant to = Instant.parse("2027-01-02T00:00:00Z");
        List<Event> events = List.of(event());
        when(eventRepository.findNormalEvents(1L, from, to)).thenReturn(events);

        // when
        List<Event> result = eventQueryService.listEvents(1L, from, to);

        // then
        assertThat(result).isSameAs(events);
        verify(eventRepository).findNormalEvents(1L, from, to);
    }

    private Event event() {
        Event event = new Event(
                "Event",
                null,
                Instant.parse("2027-01-01T01:00:00Z"),
                Instant.parse("2027-01-01T02:00:00Z"),
                false,
                "UTC",
                null,
                new Tag(TagType.DEFAULT, "기타", "#64748B"),
                new Account()
        );
        ReflectionTestUtils.setField(event, "id", 10L);
        return event;
    }
}
