package com.calio.calendar.recurrence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecurrenceEventQueryServiceTest {

    @Mock
    private RecurrenceEventRepository recurrenceEventRepository;

    @InjectMocks
    private RecurrenceEventQueryService recurrenceEventQueryService;

    @Test
    @DisplayName("계정 소유 반복 일정은 응답으로 변환해 조회한다")
    void givenOwnedRecurrenceEvent_whenGet_thenReturnsResponse() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent();
        when(recurrenceEventRepository.findByIdAndAccount_Id(10L, 1L))
                .thenReturn(Optional.of(recurrenceEvent));

        // when
        RecurrenceEventResponse response = recurrenceEventQueryService.getRecurrenceEvent(1L, 10L);

        // then
        assertThat(response.recurrenceId()).isEqualTo(10L);
        assertThat(response.title()).isEqualTo("Rule");
        assertThat(response.description()).isEqualTo("memo");
        assertThat(response.recurrence()).containsExactly("RRULE:FREQ=DAILY;COUNT=3");
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
