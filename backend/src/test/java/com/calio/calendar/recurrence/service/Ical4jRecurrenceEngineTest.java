package com.calio.calendar.recurrence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Ical4jRecurrenceEngineTest {

    private final Ical4jRecurrenceEngine recurrenceEngine = new Ical4jRecurrenceEngine();

    @Test
    @DisplayName("DTSTART, RRULE, RDATE에서 EXDATE와 EXRULE을 제외하고 중복 제거 후 정렬한다")
    void givenIncludeAndExcludeLines_whenExpand_thenAppliesRecurrenceSetSemantics() {
        // given
        RecurrenceSchedule schedule = timedSchedule("2026-07-20", "09:00", "10:00", "UTC");
        List<String> recurrence = List.of(
                "EXRULE:FREQ=DAILY;BYDAY=SA,SU",
                "RDATE:20260722T090000Z,20260727T090000Z",
                "RRULE:FREQ=DAILY;COUNT=8",
                "EXDATE:20260723T090000Z"
        );

        // when
        List<Instant> origins = recurrenceEngine.expand(
                        schedule,
                        recurrenceEngine.validate(schedule, recurrence),
                        Instant.parse("2026-07-20T00:00:00Z"),
                        Instant.parse("2026-07-29T00:00:00Z")
                ).stream()
                .map(RecurrenceOccurrence::originStartAt)
                .toList();

        // then
        assertThat(origins).containsExactly(
                Instant.parse("2026-07-20T09:00:00Z"),
                Instant.parse("2026-07-21T09:00:00Z"),
                Instant.parse("2026-07-22T09:00:00Z"),
                Instant.parse("2026-07-24T09:00:00Z"),
                Instant.parse("2026-07-27T09:00:00Z")
        );
    }

    @Test
    @DisplayName("timed recurrence는 DST 전환 뒤에도 지역 wall-clock을 유지하고 offset만 변경한다")
    void givenDailyTimedRuleAcrossDst_whenExpand_thenKeepsWallClock() {
        // given
        RecurrenceSchedule schedule = timedSchedule(
                "2026-10-31",
                "09:00",
                "10:00",
                "America/New_York"
        );

        // when
        List<RecurrenceOccurrence> occurrences = recurrenceEngine.expand(
                schedule,
                List.of("RRULE:FREQ=DAILY;COUNT=3"),
                Instant.parse("2026-10-31T00:00:00Z"),
                Instant.parse("2026-11-04T00:00:00Z")
        );

        // then
        assertThat(occurrences)
                .extracting(RecurrenceOccurrence::originStartAt)
                .containsExactly(
                        Instant.parse("2026-10-31T13:00:00Z"),
                        Instant.parse("2026-11-01T14:00:00Z"),
                        Instant.parse("2026-11-02T14:00:00Z")
                );
    }

    @Test
    @DisplayName("반복 중 DST gap에 놓인 지역 시각은 제외하고 COUNT는 iCal4j 규칙 의미를 유지한다")
    void givenGeneratedGapTime_whenExpand_thenSkipsInvalidLocalOccurrence() {
        // given
        RecurrenceSchedule schedule = timedSchedule(
                "2026-03-07",
                "02:30",
                "03:30",
                "America/New_York"
        );

        // when
        List<Instant> origins = recurrenceEngine.expand(
                        schedule,
                        List.of("RRULE:FREQ=DAILY;COUNT=3"),
                        Instant.parse("2026-03-07T00:00:00Z"),
                        Instant.parse("2026-03-11T00:00:00Z")
                ).stream()
                .map(RecurrenceOccurrence::originStartAt)
                .toList();

        // then
        assertThat(origins).containsExactly(
                Instant.parse("2026-03-07T07:30:00Z"),
                Instant.parse("2026-03-09T06:30:00Z")
        );
    }

    @Test
    @DisplayName("반복 중 DST overlap에 놓인 지역 시각은 earlier offset으로 정규화한다")
    void givenGeneratedOverlapTime_whenExpand_thenUsesEarlierOffset() {
        // given
        RecurrenceSchedule schedule = timedSchedule(
                "2026-10-31",
                "01:30",
                "02:30",
                "America/New_York"
        );

        // when
        List<Instant> origins = recurrenceEngine.expand(
                        schedule,
                        List.of("RRULE:FREQ=DAILY;COUNT=3"),
                        Instant.parse("2026-10-31T00:00:00Z"),
                        Instant.parse("2026-11-04T00:00:00Z")
                ).stream()
                .map(RecurrenceOccurrence::originStartAt)
                .toList();

        // then
        assertThat(origins).containsExactly(
                Instant.parse("2026-10-31T05:30:00Z"),
                Instant.parse("2026-11-01T05:30:00Z"),
                Instant.parse("2026-11-02T06:30:00Z")
        );
    }

    @Test
    @DisplayName("UTC RDATE는 DST overlap에서도 Instant identity를 보존한다")
    void givenUtcRdateDuringOverlap_whenExpand_thenPreservesInstantIdentity() {
        // given
        RecurrenceSchedule schedule = timedSchedule(
                "2026-10-31",
                "01:30",
                "02:30",
                "America/New_York"
        );

        // when
        List<Instant> origins = recurrenceEngine.expand(
                        schedule,
                        List.of(
                                "RRULE:FREQ=DAILY;COUNT=2",
                                "RDATE:20261101T063000Z"
                        ),
                        Instant.parse("2026-10-31T00:00:00Z"),
                        Instant.parse("2026-11-03T00:00:00Z")
                ).stream()
                .map(RecurrenceOccurrence::originStartAt)
                .toList();

        // then
        assertThat(origins).containsExactly(
                Instant.parse("2026-10-31T05:30:00Z"),
                Instant.parse("2026-11-01T05:30:00Z"),
                Instant.parse("2026-11-01T06:30:00Z")
        );
    }

    @Test
    @DisplayName("DST overlap의 첫 schedule은 earlier offset으로 정규화한다")
    void givenOverlapSchedule_whenCreate_thenUsesEarlierOffset() {
        // when
        RecurrenceSchedule schedule = RecurrenceSchedule.create(
                false,
                LocalDate.parse("2026-11-01"),
                LocalDate.parse("2026-11-01"),
                LocalTime.parse("01:30"),
                LocalTime.parse("02:30"),
                "America/New_York"
        );

        // then
        assertThat(schedule.startAt()).isEqualTo(Instant.parse("2026-11-01T05:30:00Z"));
    }

    @Test
    @DisplayName("timed 반복 기간의 endDate는 첫 occurrence duration에 포함하지 않는다")
    void givenTimedApplicationPeriod_whenExpand_thenUsesOccurrenceTimesForDuration() {
        // given
        RecurrenceSchedule schedule = timedSchedule(
                "2026-07-20",
                "2026-07-31",
                "09:00",
                "10:00",
                "UTC"
        );

        // when
        List<RecurrenceOccurrence> occurrences = recurrenceEngine.expand(
                schedule,
                List.of("RRULE:FREQ=DAILY;COUNT=2"),
                Instant.parse("2026-07-20T00:00:00Z"),
                Instant.parse("2026-07-23T00:00:00Z")
        );

        // then
        assertThat(schedule.endDate()).isEqualTo(LocalDate.parse("2026-07-31"));
        assertThat(schedule.endAt()).isEqualTo(Instant.parse("2026-07-20T10:00:00Z"));
        assertThat(occurrences)
                .extracting(RecurrenceOccurrence::startAt, RecurrenceOccurrence::endAt)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                Instant.parse("2026-07-20T09:00:00Z"),
                                Instant.parse("2026-07-20T10:00:00Z")
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                Instant.parse("2026-07-21T09:00:00Z"),
                                Instant.parse("2026-07-21T10:00:00Z")
                        )
                );
    }

    @Test
    @DisplayName("timed recurrence는 endDate까지 시작하는 occurrence만 전개한다")
    void givenTimedApplicationPeriod_whenRuleContinues_thenStopsAtEndDate() {
        // given
        RecurrenceSchedule schedule = timedSchedule(
                "2026-07-20",
                "2026-07-22",
                "09:00",
                "10:00",
                "UTC"
        );

        // when
        List<Instant> origins = recurrenceEngine.expand(
                        schedule,
                        List.of("RRULE:FREQ=DAILY"),
                        Instant.parse("2026-07-20T00:00:00Z"),
                        Instant.parse("2026-07-25T00:00:00Z")
                ).stream()
                .map(RecurrenceOccurrence::originStartAt)
                .toList();

        // then
        assertThat(origins).containsExactly(
                Instant.parse("2026-07-20T09:00:00Z"),
                Instant.parse("2026-07-21T09:00:00Z"),
                Instant.parse("2026-07-22T09:00:00Z")
        );
    }

    @Test
    @DisplayName("DST gap의 첫 schedule은 INVALID_RECURRENCE_SCHEDULE로 거부한다")
    void givenGapSchedule_whenCreate_thenRejectsSchedule() {
        assertThatThrownBy(() -> RecurrenceSchedule.create(
                false,
                LocalDate.parse("2026-03-08"),
                LocalDate.parse("2026-03-08"),
                LocalTime.parse("02:30"),
                LocalTime.parse("03:30"),
                "America/New_York"
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_RECURRENCE_SCHEDULE)
        );
    }

    @Test
    @DisplayName("all-day recurrence의 DATE-TIME RDATE는 INVALID_RECURRENCE_RULE로 거부한다")
    void givenDateTimeRdateForAllDay_whenValidate_thenRejectsValueType() {
        // given
        RecurrenceSchedule schedule = RecurrenceSchedule.create(
                true,
                LocalDate.parse("2026-07-20"),
                LocalDate.parse("2026-07-21"),
                null,
                null,
                null
        );

        // when, then
        assertThatThrownBy(() -> recurrenceEngine.validate(
                schedule,
                List.of("RDATE:20260721T000000Z")
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_RECURRENCE_RULE)
        );
        assertInvalidRule(() -> recurrenceEngine.validate(schedule, List.of("RDATE:20260721")));
    }

    @Test
    @DisplayName("all-day RDATE와 EXDATE는 VALUE=DATE 집합으로 전개한다")
    void givenAllDayDateValues_whenExpand_thenAppliesDateSetSemantics() {
        // given
        RecurrenceSchedule schedule = RecurrenceSchedule.create(
                true,
                LocalDate.parse("2026-07-20"),
                LocalDate.parse("2026-07-21"),
                null,
                null,
                null
        );

        // when
        List<Instant> origins = recurrenceEngine.expand(
                        schedule,
                        List.of(
                                "RRULE:FREQ=DAILY;COUNT=3",
                                "RDATE;VALUE=DATE:20260722",
                                "EXDATE;VALUE=DATE:20260721"
                        ),
                        Instant.parse("2026-07-20T00:00:00Z"),
                        Instant.parse("2026-07-24T00:00:00Z")
                ).stream()
                .map(RecurrenceOccurrence::originStartAt)
                .toList();

        // then
        assertThat(origins).containsExactly(
                Instant.parse("2026-07-20T00:00:00Z"),
                Instant.parse("2026-07-22T00:00:00Z")
        );
    }

    @Test
    @DisplayName("timed recurrence의 DATE RDATE와 RFC 5545 외 rule part는 INVALID_RECURRENCE_RULE로 거부한다")
    void givenInvalidTimedValueTypeOrExtension_whenValidate_thenRejectsRule() {
        // given
        RecurrenceSchedule schedule = timedSchedule("2026-07-20", "09:00", "10:00", "UTC");

        // when, then
        assertInvalidRule(() -> recurrenceEngine.validate(schedule, List.of("RDATE;VALUE=DATE:20260721")));
        assertInvalidRule(() -> recurrenceEngine.validate(schedule, List.of("RRULE:FREQ=DAILY;BYEASTER=1")));
        assertInvalidRule(() -> recurrenceEngine.validate(schedule, List.of("RRULE:FREQ=DAILY;FREQ=WEEKLY")));
    }

    @Test
    @DisplayName("DTSTART를 EXDATE로 제거하거나 inclusion line이 없으면 INVALID_RECURRENCE_RULE로 거부한다")
    void givenDefinitionWithoutFirstOccurrence_whenValidate_thenRejectsRule() {
        // given
        RecurrenceSchedule schedule = timedSchedule("2026-07-20", "09:00", "10:00", "UTC");

        // when, then
        assertInvalidRule(() -> recurrenceEngine.validate(schedule, List.of(
                "RRULE:FREQ=DAILY;COUNT=3",
                "EXDATE:20260720T090000Z"
        )));
        assertInvalidRule(() -> recurrenceEngine.validate(schedule, List.of("EXDATE:20260721T090000Z")));
        assertInvalidRule(() -> recurrenceEngine.validate(schedule, List.of()));
    }

    @Test
    @DisplayName("무기한 RRULE도 요청한 범위 안의 occurrence만 반환한다")
    void givenUnboundedRule_whenExpand_thenReturnsOnlyRequestedWindow() {
        // given
        RecurrenceSchedule schedule = timedSchedule("2020-01-01", "09:00", "10:00", "UTC");

        // when
        List<RecurrenceOccurrence> occurrences = recurrenceEngine.expand(
                schedule,
                List.of("RRULE:FREQ=DAILY"),
                Instant.parse("2026-07-20T00:00:00Z"),
                Instant.parse("2026-07-23T00:00:00Z")
        );

        // then
        assertThat(occurrences).hasSize(3);
        assertThat(occurrences.getFirst().originStartAt()).isEqualTo(Instant.parse("2026-07-20T09:00:00Z"));
    }

    @Test
    @DisplayName("BYMONTHDAY=-1은 윤년 2월을 포함한 실제 월말 occurrence를 생성한다")
    void givenLastDayOfMonthRule_whenExpand_thenHandlesLeapYearMonthEnd() {
        // given
        RecurrenceSchedule schedule = timedSchedule("2024-01-31", "09:00", "10:00", "UTC");

        // when
        List<Instant> origins = recurrenceEngine.expand(
                        schedule,
                        List.of("RRULE:FREQ=MONTHLY;BYMONTHDAY=-1;COUNT=3"),
                        Instant.parse("2024-01-01T00:00:00Z"),
                        Instant.parse("2024-04-01T00:00:00Z")
                ).stream()
                .map(RecurrenceOccurrence::originStartAt)
                .toList();

        // then
        assertThat(origins).containsExactly(
                Instant.parse("2024-01-31T09:00:00Z"),
                Instant.parse("2024-02-29T09:00:00Z"),
                Instant.parse("2024-03-31T09:00:00Z")
        );
    }

    @Test
    @DisplayName("BYHOUR와 UTC UNTIL은 DTSTART를 유지하면서 지정 시각과 종료 경계를 적용한다")
    void givenByHourAndUntil_whenExpand_thenAppliesTimedRuleParts() {
        // given
        RecurrenceSchedule schedule = timedSchedule("2024-02-28", "09:00", "10:00", "UTC");

        // when
        List<Instant> origins = recurrenceEngine.expand(
                        schedule,
                        List.of("RRULE:FREQ=DAILY;BYHOUR=11;UNTIL=20240301T235959Z"),
                        Instant.parse("2024-02-28T00:00:00Z"),
                        Instant.parse("2024-03-03T00:00:00Z")
                ).stream()
                .map(RecurrenceOccurrence::originStartAt)
                .toList();

        // then
        assertThat(origins).containsExactly(
                Instant.parse("2024-02-28T09:00:00Z"),
                Instant.parse("2024-02-28T11:00:00Z"),
                Instant.parse("2024-02-29T11:00:00Z"),
                Instant.parse("2024-03-01T11:00:00Z")
        );
    }

    @Test
    @DisplayName("content line은 속성·parameter 순서로 정규화되고 중복 line은 set으로 축약된다")
    void givenUnorderedDuplicateLines_whenValidate_thenReturnsDeterministicCanonicalLines() {
        // given
        RecurrenceSchedule schedule = timedSchedule("2026-07-20", "09:00", "10:00", "UTC");

        // when
        List<String> normalized = recurrenceEngine.validate(schedule, List.of(
                "EXDATE;value=date-time;tzid=UTC:20260721T090000",
                "rrule:freq=daily;count=3",
                "RRULE:FREQ=DAILY;COUNT=3"
        ));

        // then
        assertThat(normalized).containsExactly(
                "RRULE:FREQ=DAILY;COUNT=3",
                "EXDATE;TZID=UTC;VALUE=DATE-TIME:20260721T090000"
        );
    }

    private void assertInvalidRule(Runnable validation) {
        assertThatThrownBy(validation::run)
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_RECURRENCE_RULE)
                );
    }

    private RecurrenceSchedule timedSchedule(
            String date,
            String startTime,
            String endTime,
            String timeZone
    ) {
        LocalDate startDate = LocalDate.parse(date);
        return timedSchedule(
                date,
                startDate.plusYears(10).toString(),
                startTime,
                endTime,
                timeZone
        );
    }

    private RecurrenceSchedule timedSchedule(
            String startDate,
            String endDate,
            String startTime,
            String endTime,
            String timeZone
    ) {
        return RecurrenceSchedule.create(
                false,
                LocalDate.parse(startDate),
                LocalDate.parse(endDate),
                LocalTime.parse(startTime),
                LocalTime.parse(endTime),
                timeZone
        );
    }
}
