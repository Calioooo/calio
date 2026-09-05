package com.calio.calendar.aicalendar.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CalendarAiMutationPolicyTest {

    private final CalendarAiMutationPolicy policy = new CalendarAiMutationPolicy(
            Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    @DisplayName("AI 반복 생성은 최대 종료일을 허용한다")
    void givenMaximumEndDate_whenValidateCreateRecurrence_thenAccepts() {
        RecurrenceSchedule schedule = timedSchedule(
                "2026-08-07T09:18:00Z", "2026-08-07T10:18:00Z"
        );

        policy.validateCreateRecurrence(
                List.of("RRULE:FREQ=WEEKLY;UNTIL=21261231T091800Z"),
                schedule
        );
    }

    @Test
    @DisplayName("AI 반복 생성은 종료일 없음과 종일 일정을 허용한다")
    void givenOptionalEndDate_whenValidateCreateRecurrence_thenAccepts() {
        RecurrenceSchedule allDay = RecurrenceSchedule.create(
                true,
                Instant.parse("2026-08-07T00:00:00Z"),
                Instant.parse("2026-08-08T00:00:00Z"),
                null
        );

        policy.validateCreateRecurrence(List.of("RRULE:FREQ=DAILY"), allDay);
        policy.validateCreateRecurrence(List.of("RRULE:FREQ=MONTHLY;UNTIL=20261231"), allDay);
    }

    @Test
    @DisplayName("AI 반복 생성은 timed UNTIL을 일정 timezone의 종료일로 판단한다")
    void givenTimedUntilOnPreviousUtcDate_whenValidateCreateRecurrence_thenAcceptsLocalStartDate() {
        RecurrenceSchedule schedule = RecurrenceSchedule.create(
                false,
                Instant.parse("2026-08-06T10:30:00Z"),
                Instant.parse("2026-08-06T11:30:00Z"),
                "Pacific/Kiritimati"
        );

        policy.validateCreateRecurrence(
                List.of("RRULE:FREQ=WEEKLY;UNTIL=20260806T103000Z"),
                schedule
        );
    }

    @Test
    @DisplayName("AI 반복 회차 태그 변경은 기존 태그와 다르면 거절한다")
    void givenDifferentOccurrenceTag_whenValidateOccurrenceTagChange_thenRejects() {
        assertThatThrownBy(() -> policy.validateOccurrenceTagChange(2L, 1L))
                .isInstanceOf(CalioException.class)
                .extracting(exception -> ((CalioException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RECURRENCE_OCCURRENCE_TAG_CHANGE_NOT_SUPPORTED);
    }

    @Test
    @DisplayName("AI 반복 생성은 최대 종료일 이후와 초 단위 시간을 거절한다")
    void givenUnsupportedBoundaryOrPrecision_whenValidateCreateRecurrence_thenRejects() {
        assertThatThrownBy(() -> policy.validateCreateRecurrence(
                List.of("RRULE:FREQ=YEARLY;UNTIL=21270101T091800Z"),
                timedSchedule("2026-08-07T09:18:00Z", "2026-08-07T10:18:00Z")
        )).isInstanceOf(CalioException.class)
                .extracting(exception -> ((CalioException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_RECURRENCE_SCHEDULE);

        assertThatThrownBy(() -> policy.validateCreateRecurrence(
                List.of("RRULE:FREQ=WEEKLY;UNTIL=20260806T091800Z"),
                timedSchedule("2026-08-07T09:18:00Z", "2026-08-07T10:18:00Z")
        )).isInstanceOf(CalioException.class)
                .extracting(exception -> ((CalioException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_RECURRENCE_SCHEDULE);

        assertThatThrownBy(() -> policy.validateCreateRecurrence(
                List.of("RRULE:FREQ=DAILY"),
                timedSchedule("2026-08-07T09:18:01Z", "2026-08-07T10:18:00Z")
        )).isInstanceOf(CalioException.class)
                .extracting(exception -> ((CalioException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
    }

    private RecurrenceSchedule timedSchedule(String startAt, String endAt) {
        return RecurrenceSchedule.create(false, Instant.parse(startAt), Instant.parse(endAt), "UTC");
    }
}
