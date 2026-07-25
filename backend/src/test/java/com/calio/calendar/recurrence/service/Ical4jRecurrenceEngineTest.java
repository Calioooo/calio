package com.calio.calendar.recurrence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Ical4jRecurrenceEngineTest {

    private final Ical4jRecurrenceEngine engine = new Ical4jRecurrenceEngine();

    @Test
    @DisplayName("여러 RRULE과 RDATE의 합집합에서 여러 EXDATE와 EXRULE을 제외한다")
    void givenMultipleInclusionAndExclusionLines_whenExpand_thenUsesSetSemantics() {
        // given
        RecurrenceSchedule schedule = timedSchedule(
                "2026-07-20T09:00:00",
                "2026-07-20T10:00:00",
                "UTC"
        );
        List<String> recurrenceRules = List.of(
                "RRULE:FREQ=DAILY;COUNT=4",
                "RRULE:FREQ=WEEKLY;COUNT=2;BYDAY=MO",
                "RDATE:20260725T090000Z",
                "RDATE:20260726T090000Z",
                "EXDATE:20260722T090000Z",
                "EXDATE:20260723T090000Z",
                "EXRULE:FREQ=WEEKLY;COUNT=2;BYDAY=TU",
                "EXRULE:FREQ=WEEKLY;COUNT=2;BYDAY=SU"
        );

        // when
        List<RecurrenceOccurrence> occurrences = engine.expand(
                schedule,
                recurrenceRules,
                Instant.parse("2026-07-20T00:00:00Z"),
                Instant.parse("2026-07-29T00:00:00Z")
        );

        // then
        assertThat(occurrences)
                .extracting(RecurrenceOccurrence::originStartAt)
                .containsExactly(
                        Instant.parse("2026-07-20T09:00:00Z"),
                        Instant.parse("2026-07-25T09:00:00Z"),
                        Instant.parse("2026-07-27T09:00:00Z")
                );
    }

    @Test
    @DisplayName("content line은 property 순서와 문자열 순서로 정규화하고 중복을 제거한다")
    void givenUnorderedDuplicateLines_whenValidate_thenReturnsCanonicalOrder() {
        // given
        RecurrenceSchedule schedule = timedSchedule(
                "2026-07-20T09:00:00",
                "2026-07-20T10:00:00",
                "Asia/Seoul"
        );

        // when
        List<String> normalized = engine.validate(schedule, List.of(
                "EXDATE;TZID=Asia/Seoul:20260723T090000",
                "RDATE;TZID=Asia/Seoul:20260725T090000",
                "RRULE:FREQ=WEEKLY;BYDAY=MO",
                "RRULE:FREQ=DAILY;COUNT=3",
                "EXDATE;TZID=Asia/Seoul:20260723T090000",
                "EXRULE:FREQ=MONTHLY;BYMONTHDAY=31"
        ));

        // then
        assertThat(normalized).containsExactly(
                "RRULE:FREQ=DAILY;COUNT=3",
                "RRULE:FREQ=WEEKLY;BYDAY=MO",
                "RDATE;TZID=Asia/Seoul:20260725T090000",
                "EXDATE;TZID=Asia/Seoul:20260723T090000",
                "EXRULE:FREQ=MONTHLY;BYMONTHDAY=31"
        );
    }

    @Test
    @DisplayName("RDATE만 있는 반복 정의도 first occurrence와 추가 날짜를 반환한다")
    void givenRDateOnlyDefinition_whenExpand_thenIncludesFirstAndAdditionalDates() {
        // given
        RecurrenceSchedule schedule = timedSchedule(
                "2026-08-01T09:00:00",
                "2026-08-01T10:00:00",
                "UTC"
        );

        // when
        List<RecurrenceOccurrence> occurrences = engine.expand(
                schedule,
                List.of("RDATE:20260803T090000Z,20260805T090000Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-06T00:00:00Z")
        );

        // then
        assertThat(occurrences)
                .extracting(RecurrenceOccurrence::originStartAt)
                .containsExactly(
                        Instant.parse("2026-08-01T09:00:00Z"),
                        Instant.parse("2026-08-03T09:00:00Z"),
                        Instant.parse("2026-08-05T09:00:00Z")
                );
    }

    @Test
    @DisplayName("COUNT와 UNTIL이 없는 RRULE은 요청 범위에서만 무기한 전개한다")
    void givenUnboundedRule_whenExpand_thenReturnsOnlyRequestedRange() {
        // given
        RecurrenceSchedule schedule = timedSchedule(
                "2020-01-01T09:00:00",
                "2020-01-01T10:00:00",
                "UTC"
        );

        // when
        List<RecurrenceOccurrence> occurrences = engine.expand(
                schedule,
                List.of("RRULE:FREQ=DAILY"),
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-04T00:00:00Z")
        );

        // then
        assertThat(occurrences)
                .extracting(RecurrenceOccurrence::originStartAt)
                .containsExactly(
                        Instant.parse("2026-08-01T09:00:00Z"),
                        Instant.parse("2026-08-02T09:00:00Z"),
                        Instant.parse("2026-08-03T09:00:00Z")
                );
    }

    @Test
    @DisplayName("timed recurrence는 DST 이후에도 first occurrence의 지역 시간을 유지한다")
    void givenTimedScheduleAcrossDst_whenExpand_thenPreservesWallClock() {
        // given
        RecurrenceSchedule schedule = timedSchedule(
                "2026-03-01T09:00:00",
                "2026-03-01T10:00:00",
                "America/New_York"
        );

        // when
        List<RecurrenceOccurrence> occurrences = engine.expand(
                schedule,
                List.of("RRULE:FREQ=WEEKLY;COUNT=3"),
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-03-20T00:00:00Z")
        );

        // then
        assertThat(occurrences)
                .extracting(RecurrenceOccurrence::originStartAt)
                .containsExactly(
                        Instant.parse("2026-03-01T14:00:00Z"),
                        Instant.parse("2026-03-08T13:00:00Z"),
                        Instant.parse("2026-03-15T13:00:00Z")
                );
    }

    @Test
    @DisplayName("all-day first occurrence의 exclusive 기간을 모든 occurrence에 유지한다")
    void givenMultiDayAllDaySchedule_whenExpand_thenPreservesDayDuration() {
        // given
        RecurrenceSchedule schedule = RecurrenceSchedule.create(
                true,
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-03T00:00:00Z"),
                null
        );

        // when
        List<RecurrenceOccurrence> occurrences = engine.expand(
                schedule,
                List.of("RRULE:FREQ=DAILY;COUNT=2"),
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-05T00:00:00Z")
        );

        // then
        assertThat(occurrences)
                .extracting(RecurrenceOccurrence::startAt, RecurrenceOccurrence::endAt)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                Instant.parse("2026-09-01T00:00:00Z"),
                                Instant.parse("2026-09-03T00:00:00Z")
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                Instant.parse("2026-09-02T00:00:00Z"),
                                Instant.parse("2026-09-04T00:00:00Z")
                        )
                );
    }

    @Test
    @DisplayName("first occurrence보다 이른 RDATE는 canonical first 의미를 깨므로 거부한다")
    void givenEarlierRDate_whenValidate_thenRejectsRule() {
        // given
        RecurrenceSchedule schedule = timedSchedule(
                "2026-08-03T09:00:00",
                "2026-08-03T10:00:00",
                "UTC"
        );

        // when, then
        assertErrorCode(
                () -> engine.validate(schedule, List.of("RDATE:20260801T090000Z")),
                ErrorCode.INVALID_RECURRENCE_RULE
        );
    }

    @Test
    @DisplayName("EXDATE가 canonical first occurrence를 제거하면 거부한다")
    void givenFirstOccurrenceExDate_whenValidate_thenRejectsRule() {
        // given
        RecurrenceSchedule schedule = timedSchedule(
                "2026-08-03T09:00:00",
                "2026-08-03T10:00:00",
                "UTC"
        );

        // when, then
        assertErrorCode(
                () -> engine.validate(schedule, List.of(
                        "RRULE:FREQ=DAILY;COUNT=3",
                        "EXDATE:20260803T090000Z"
                )),
                ErrorCode.INVALID_RECURRENCE_RULE
        );
    }

    @Test
    @DisplayName("all-day schedule은 UTC midnight Instant와 null timezone만 허용한다")
    void givenInvalidAllDaySchedule_whenCreate_thenRejectsSchedule() {
        assertErrorCode(
                () -> RecurrenceSchedule.create(
                        true,
                        Instant.parse("2026-08-01T01:00:00Z"),
                        Instant.parse("2026-08-02T01:00:00Z"),
                        null
                ),
                ErrorCode.INVALID_RECURRENCE_SCHEDULE
        );
        assertErrorCode(
                () -> RecurrenceSchedule.create(
                        true,
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-02T00:00:00Z"),
                        "UTC"
                ),
                ErrorCode.INVALID_RECURRENCE_SCHEDULE
        );
    }

    @Test
    @DisplayName("timed schedule의 잘못된 timezone은 명시적인 오류로 거부한다")
    void givenInvalidTimeZone_whenCreate_thenRejectsTimeZone() {
        assertErrorCode(
                () -> RecurrenceSchedule.create(
                        false,
                        Instant.parse("2026-08-01T09:00:00Z"),
                        Instant.parse("2026-08-01T10:00:00Z"),
                        "Mars/Olympus"
                ),
                ErrorCode.INVALID_TIME_ZONE
        );
    }

    @Test
    @DisplayName("한 번의 전개에서 distinct temporal 후보가 10,000개를 넘으면 중단한다")
    void givenDenseRule_whenExpandThenExceedsLimit() {
        // given
        RecurrenceSchedule schedule = timedSchedule(
                "2026-08-01T00:00:00",
                "2026-08-01T00:00:01",
                "UTC"
        );

        // when, then
        assertErrorCode(
                () -> engine.expand(
                        schedule,
                        List.of("RRULE:FREQ=SECONDLY"),
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-01T03:00:00Z")
                ),
                ErrorCode.RECURRENCE_OCCURRENCE_LIMIT_EXCEEDED
        );
    }

    @Test
    @DisplayName("containsOrigin은 여러 inclusion과 exclusion line의 최종 집합을 사용한다")
    void givenIncludedAndExcludedOrigins_whenContainsOrigin_thenUsesFinalSet() {
        // given
        RecurrenceSchedule schedule = timedSchedule(
                "2026-08-01T09:00:00",
                "2026-08-01T10:00:00",
                "UTC"
        );
        List<String> recurrenceRules = List.of(
                "RRULE:FREQ=DAILY;COUNT=3",
                "RDATE:20260805T090000Z",
                "EXDATE:20260802T090000Z"
        );

        // when, then
        assertThat(engine.containsOrigin(
                schedule,
                recurrenceRules,
                Instant.parse("2026-08-05T09:00:00Z")
        )).isTrue();
        assertThat(engine.containsOrigin(
                schedule,
                recurrenceRules,
                Instant.parse("2026-08-02T09:00:00Z")
        )).isFalse();
    }

    private RecurrenceSchedule timedSchedule(
            String localStart,
            String localEnd,
            String timeZone
    ) {
        ZoneId zoneId = ZoneId.of(timeZone);
        return RecurrenceSchedule.create(
                false,
                LocalDateTime.parse(localStart).atZone(zoneId).toInstant(),
                LocalDateTime.parse(localEnd).atZone(zoneId).toInstant(),
                timeZone
        );
    }

    private void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        CalioException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode)
                );
    }
}
