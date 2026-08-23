package com.calio.calendar.groupcalendar.sharing.recurrence.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PersonalRecurrenceGroupShareTest {

    @Test
    @DisplayName("새 반복 공유 mapping은 원본 상세를 기본으로 숨긴다")
    void givenNewShare_whenCreated_thenHidesOriginalDetails() {
        // when
        PersonalRecurrenceGroupShare share = share();

        // then
        assertThat(share.isShowOriginalDetails()).isFalse();
    }

    @Test
    @DisplayName("반복 공유는 원본 상세 공개 여부만 변경할 수 있다")
    void givenShare_whenChangeOriginalDetailsVisibility_thenUpdatesVisibility() {
        // given
        PersonalRecurrenceGroupShare share = share();

        // when
        share.changeOriginalDetailsVisibility(true);

        // then
        assertThat(share.isShowOriginalDetails()).isTrue();
    }

    private PersonalRecurrenceGroupShare share() {
        return new PersonalRecurrenceGroupShare(
                new RecurrenceEvent(
                        "원본 반복 일정",
                        "원본 설명",
                        new RecurrenceSchedule(
                                Instant.parse("2028-01-01T09:00:00Z"),
                                Instant.parse("2028-01-01T10:00:00Z"),
                                false,
                                "UTC"
                        ),
                        List.of("RRULE:FREQ=WEEKLY"),
                        null,
                        null
                ),
                null
        );
    }
}
