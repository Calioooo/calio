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
    @DisplayName("조회 범위가 넓어도 반복 적용 기간 안에서만 occurrence를 확장한다")
    void givenWideQueryRange_whenExpand_thenLimitsExpansionToApplicationPeriod() {
        // given
        RecurrenceSchedule schedule = timedSchedule(
                "2026-07-20",
                "2026-07-20",
                "09:00",
                "10:00",
                "UTC"
        );

        // when
        List<Instant> origins = recurrenceEngine.expand(
                        schedule,
                        List.of("RRULE:FREQ=DAILY"),
                        Instant.parse("2000-01-01T00:00:00Z"),
                        Instant.parse("2100-01-01T00:00:00Z")
                ).stream()
                .map(RecurrenceOccurrence::originStartAt)
                .toList();

        // then
        assertThat(origins).containsExactly(Instant.parse("2026-07-20T09:00:00Z"));
    }

    @Test
    @DisplayName("단일 RRULE에서 EXDATE에 해당하는 occurrence를 제외한다")
    void givenRuleAndExdates_whenExpand_thenExcludesMatchingOccurrences() {
        // given
        RecurrenceSchedule schedule = timedSchedule("2026-07-20", "09:00", "10:00", "UTC");
        List<String> recurrence = List.of(
                "RRULE:FREQ=DAILY;COUNT=8",
                "EXDATE:20260723T090000Z,20260725T090000Z,20260726T090000Z"
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
    @DisplayName("UTC EXDATE는 DST overlap에서도 정확한 Instant만 제외한다")
    void givenUtcExdateDuringOverlap_whenExpand_thenMatchesExactInstant() {
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
                                "RRULE:FREQ=DAILY;COUNT=3",
                                "EXDATE:20261101T063000Z"
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
                Instant.parse("2026-11-02T06:30:00Z")
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
        assertThat(schedule.startDate()).isEqualTo(LocalDate.parse("2026-11-01"));
        assertThat(schedule.startTime()).isEqualTo(LocalTime.parse("01:30"));
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
    @DisplayName("timed recurrence expands from first matching occurrence after startDate")
    void givenApplicationPeriodStartsBeforeRuleDay_whenExpand_thenStartsAtFirstMatchingOccurrence() {
        // given
        RecurrenceSchedule schedule = timedSchedule(
                "2026-07-01",
                "2026-07-31",
                "09:00",
                "10:00",
                "UTC"
        );

        // when
        List<Instant> origins = recurrenceEngine.expand(
                        schedule,
                        recurrenceEngine.validate(schedule, List.of("RRULE:FREQ=WEEKLY;BYDAY=TH;COUNT=3")),
                        Instant.parse("2026-07-01T00:00:00Z"),
                        Instant.parse("2026-07-31T23:59:59Z")
                ).stream()
                .map(RecurrenceOccurrence::originStartAt)
                .toList();

        // then
        assertThat(origins).containsExactly(
                Instant.parse("2026-07-02T09:00:00Z"),
                Instant.parse("2026-07-09T09:00:00Z"),
                Instant.parse("2026-07-16T09:00:00Z")
        );
    }

    @Test
    @DisplayName("적용 기간 안에 첫 occurrence가 없는 반복 규칙은 거부한다")
    void givenNoMatchingOccurrenceInApplicationPeriod_whenValidate_thenRejectsRule() {
        // given
        RecurrenceSchedule schedule = timedSchedule(
                "2026-07-01",
                "2026-07-01",
                "09:00",
                "10:00",
                "UTC"
        );

        // when, then
        assertInvalidRule(() -> recurrenceEngine.validate(
                schedule,
                List.of("RRULE:FREQ=WEEKLY;BYDAY=TH;COUNT=3")
        ));
    }

    @Test
    @DisplayName("timed recurrence expands only occurrences starting by endDate")
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
    @DisplayName("지원하지 않는 RDATE와 EXRULE은 거부한다")
    void givenUnsupportedRecurrenceLine_whenValidate_thenRejectsRule() {
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
        assertInvalidRule(() -> recurrenceEngine.validate(
                schedule,
                List.of("RRULE:FREQ=DAILY", "RDATE;VALUE=DATE:20260721")
        ));
        assertInvalidRule(() -> recurrenceEngine.validate(
                schedule,
                List.of("RRULE:FREQ=DAILY", "EXRULE:FREQ=WEEKLY;BYDAY=SA,SU")
        ));
    }

    @Test
    @DisplayName("all-day EXDATE는 VALUE=DATE occurrence를 제외한다")
    void givenAllDayExdate_whenExpand_thenExcludesDateOccurrence() {
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
    @DisplayName("잘못된 EXDATE 타입과 RFC 5545 외 rule part는 거부한다")
    void givenInvalidTimedValueTypeOrExtension_whenValidate_thenRejectsRule() {
        // given
        RecurrenceSchedule schedule = timedSchedule("2026-07-20", "09:00", "10:00", "UTC");

        // when, then
        assertInvalidRule(() -> recurrenceEngine.validate(
                schedule,
                List.of("RRULE:FREQ=DAILY", "EXDATE;VALUE=DATE:20260721")
        ));
        assertInvalidRule(() -> recurrenceEngine.validate(schedule, List.of("RRULE:FREQ=DAILY;BYEASTER=1")));
        assertInvalidRule(() -> recurrenceEngine.validate(schedule, List.of("RRULE:FREQ=DAILY;FREQ=WEEKLY")));
        assertInvalidRule(() -> recurrenceEngine.validate(
                schedule,
                List.of("RRULE:FREQ=DAILY", "RRULE:FREQ=WEEKLY;BYDAY=MO")
        ));
    }

    @Test
    @DisplayName("definition with no inclusion line is rejected")
    void givenDefinitionWithoutFirstOccurrence_whenValidate_thenRejectsRule() {
        // given
        RecurrenceSchedule schedule = timedSchedule("2026-07-20", "09:00", "10:00", "UTC");

        // when, then
        assertThat(recurrenceEngine.validate(schedule, List.of(
                "RRULE:FREQ=DAILY;COUNT=3",
                "EXDATE:20260720T090000Z"
        ))).containsExactly(
                "RRULE:FREQ=DAILY;COUNT=3",
                "EXDATE:20260720T090000Z"
        );
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
                Instant.parse("2024-02-28T11:00:00Z"),
                Instant.parse("2024-02-29T11:00:00Z"),
                Instant.parse("2024-03-01T11:00:00Z")
        );
    }

    @Test
    @DisplayName("한 번의 확장에서 occurrence 상한을 초과하면 명시적인 오류로 중단한다")
    void givenDenseRule_whenExpand_thenRejectsOccurrenceLimitExceeded() {
        // given
        RecurrenceSchedule schedule = timedSchedule("2026-07-20", "09:00", "10:00", "UTC");

        // when, then
        assertThatThrownBy(() -> recurrenceEngine.expand(
                schedule,
                List.of("RRULE:FREQ=SECONDLY"),
                Instant.parse("2026-07-20T09:00:00Z"),
                Instant.parse("2026-07-20T13:00:00Z")
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.RECURRENCE_OCCURRENCE_LIMIT_EXCEEDED)
        );
    }

    @Test
    @DisplayName("양수 offset 일정은 UTC UNTIL과 같은 instant의 occurrence를 포함한다")
    void givenPositiveOffsetAndUtcUntil_whenExpand_thenIncludesOccurrenceAtCutoffInstant() {
        // given
        RecurrenceSchedule schedule = timedSchedule(
                "2026-01-01",
                "2026-01-05",
                "09:00",
                "10:00",
                "Asia/Seoul"
        );

        // when
        List<Instant> origins = recurrenceEngine.expand(
                        schedule,
                        List.of("RRULE:FREQ=DAILY;UNTIL=20260102T000000Z"),
                        Instant.parse("2025-12-31T00:00:00Z"),
                        Instant.parse("2026-01-03T00:00:00Z")
                ).stream()
                .map(RecurrenceOccurrence::originStartAt)
                .toList();

        // then
        assertThat(origins).containsExactly(
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
        );
    }

    @Test
    @DisplayName("음수 offset 일정은 UTC UNTIL 이후 instant의 occurrence를 제외한다")
    void givenNegativeOffsetAndUtcUntil_whenExpand_thenExcludesOccurrenceAfterCutoffInstant() {
        // given
        RecurrenceSchedule schedule = timedSchedule(
                "2026-01-01",
                "2026-01-05",
                "09:00",
                "10:00",
                "America/New_York"
        );

        // when
        List<Instant> origins = recurrenceEngine.expand(
                        schedule,
                        List.of("RRULE:FREQ=DAILY;UNTIL=20260102T120000Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-03T23:59:59Z")
                ).stream()
                .map(RecurrenceOccurrence::originStartAt)
                .toList();

        // then
        assertThat(origins).containsExactly(Instant.parse("2026-01-01T14:00:00Z"));
    }

    @Test
    @DisplayName("all-day endDate는 occurrence 기간만 결정하고 RRULE 시리즈를 자르지 않는다")
    void givenAllDayDurationAndCountRule_whenExpandLaterRange_thenReturnsLaterOccurrence() {
        // given
        RecurrenceSchedule schedule = RecurrenceSchedule.create(
                true,
                LocalDate.parse("2026-09-01"),
                LocalDate.parse("2026-09-02"),
                null,
                null,
                null
        );

        // when
        List<RecurrenceOccurrence> occurrences = recurrenceEngine.expand(
                schedule,
                List.of("RRULE:FREQ=DAILY;COUNT=10"),
                Instant.parse("2026-09-05T00:00:00Z"),
                Instant.parse("2026-09-06T00:00:00Z")
        );

        // then
        assertThat(occurrences)
                .extracting(RecurrenceOccurrence::startAt, RecurrenceOccurrence::endAt)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        Instant.parse("2026-09-05T00:00:00Z"),
                        Instant.parse("2026-09-06T00:00:00Z")
                ));
    }

    @Test
    @DisplayName("all-day 첫 occurrence는 occurrence endDate 이후라도 RRULE에서 찾는다")
    void givenAllDayRuleStartingAfterOccurrenceEnd_whenValidateAndExpand_thenUsesRuleStart() {
        // given
        RecurrenceSchedule schedule = RecurrenceSchedule.create(
                true,
                LocalDate.parse("2026-09-01"),
                LocalDate.parse("2026-09-02"),
                null,
                null,
                null
        );
        List<String> recurrence = List.of("RRULE:FREQ=WEEKLY;BYDAY=FR;COUNT=2");

        // when
        List<String> normalized = recurrenceEngine.validate(schedule, recurrence);
        List<Instant> origins = recurrenceEngine.expand(
                        schedule,
                        normalized,
                        Instant.parse("2026-09-01T00:00:00Z"),
                        Instant.parse("2026-09-12T00:00:00Z")
                ).stream()
                .map(RecurrenceOccurrence::originStartAt)
                .toList();

        // then
        assertThat(origins).containsExactly(
                Instant.parse("2026-09-04T00:00:00Z"),
                Instant.parse("2026-09-11T00:00:00Z")
        );
    }

    @Test
    @DisplayName("1,000개가 넘는 빈 frequency period 뒤의 occurrence도 계속 생성한다")
    void givenFilteredMinutelyRule_whenExpandAcrossDays_thenDoesNotStopAtEmptyPeriodLimit() {
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
                        List.of("RRULE:FREQ=MINUTELY;BYHOUR=9;BYMINUTE=0;COUNT=3"),
                        Instant.parse("2026-07-20T00:00:00Z"),
                        Instant.parse("2026-07-23T00:00:00Z")
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
    @DisplayName("all-day 첫 occurrence가 400년 이후라도 유효한 RRULE로 처리한다")
    void givenAllDayIntervalBeyondFourHundredYears_whenValidateAndExpand_thenFindsOccurrence() {
        // given
        RecurrenceSchedule schedule = RecurrenceSchedule.create(
                true,
                LocalDate.parse("2026-12-31"),
                LocalDate.parse("2027-01-01"),
                null,
                null,
                null
        );
        List<String> recurrence = List.of("RRULE:FREQ=YEARLY;INTERVAL=500;BYMONTH=1");

        // when
        List<String> normalized = recurrenceEngine.validate(schedule, recurrence);
        List<RecurrenceOccurrence> occurrences = recurrenceEngine.expand(
                schedule,
                normalized,
                Instant.parse("2526-01-01T00:00:00Z"),
                Instant.parse("2526-02-01T00:00:00Z")
        );

        // then
        assertThat(occurrences).hasSize(1);
        assertThat(occurrences.getFirst().startAt())
                .isEqualTo(Instant.parse("2526-01-31T00:00:00Z"));
    }

    @Test
    @DisplayName("RRULE과 EXDATE를 정규화하고 중복 EXDATE를 제거한다")
    void givenUnorderedDuplicateLines_whenValidate_thenReturnsDeterministicCanonicalLines() {
        // given
        RecurrenceSchedule schedule = timedSchedule("2026-07-20", "09:00", "10:00", "UTC");

        // when
        List<String> normalized = recurrenceEngine.validate(schedule, List.of(
                "EXDATE;value=date-time;tzid=UTC:20260721T090000",
                "rrule:freq=daily;count=3",
                "EXDATE;value=date-time;tzid=UTC:20260721T090000"
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
