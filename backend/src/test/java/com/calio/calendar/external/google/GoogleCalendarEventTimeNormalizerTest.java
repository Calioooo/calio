package com.calio.calendar.external.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.service.dto.NormalizedEventSchedule;
import com.calio.calendar.external.google.service.dto.NormalizedEventTime;
import com.calio.calendar.external.google.dto.GoogleCalendarEventTimeResponse;
import java.time.Instant;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarEventTimeNormalizerTest {

    private final GoogleCalendarEventTimeNormalizer normalizer =
            new GoogleCalendarEventTimeNormalizer();

    @Test
    @DisplayName("all-day date는 system timezone과 provider timezone을 사용하지 않고 UTC 자정으로 정규화한다")
    void givenAllDayDate_whenNormalize_thenUsesSameDateAtUtcMidnight() {
        // given
        TimeZone originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
        try {
            // when
            NormalizedEventTime result = normalizer.normalize(
                    new GoogleCalendarEventTimeResponse("2026-07-20", null, "Asia/Seoul")
            );

            // then
            assertThat(result.instant()).isEqualTo(Instant.parse("2026-07-20T00:00:00Z"));
            assertThat(result.allDay()).isTrue();
            assertThat(result.timeZone()).isNull();
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    @DisplayName("offset 값이 없는 시각은 IANA zone의 offset을 적용한다")
    void givenOffsetlessOverlap_whenNormalize_thenUsesEarlierOffset() {
        // when
        NormalizedEventTime result = normalizer.normalize(
                new GoogleCalendarEventTimeResponse(
                        null,
                        "2026-11-01T01:30:00",
                        "America/New_York"
                )
        );

        // then
        assertThat(result.instant()).isEqualTo(Instant.parse("2026-11-01T05:30:00Z"));
    }

    @Test
    @DisplayName("offset과 IANA zone rule이 일치하면 요청한 offset으로 Instant를 계산한다")
    void givenMatchingOffsetAndZone_whenNormalize_thenUsesSuppliedOffset() {
        // when
        NormalizedEventTime result = normalizer.normalize(
                new GoogleCalendarEventTimeResponse(
                        null,
                        "2026-07-20T09:00:00+09:00",
                        "Asia/Seoul"
                )
        );

        // then
        assertThat(result.instant()).isEqualTo(Instant.parse("2026-07-20T00:00:00Z"));
    }

    @Test
    @DisplayName("active timed schedule은 IANA timezone이 동일하지 않으면 예외를 반환한다")
    void givenDifferentTimedZones_whenNormalizeSchedule_thenRejectsProviderResponse() {
        // when, then
        assertInvalidResponse(() -> normalizer.normalizeSchedule(
                new GoogleCalendarEventTimeResponse(
                        null,
                        "2026-07-20T09:00:00+09:00",
                        "Asia/Seoul"
                ),
                new GoogleCalendarEventTimeResponse(
                        null,
                        "2026-07-20T01:00:00Z",
                        "UTC"
                )
        ));
    }

    @Test
    @DisplayName("일반 이벤트는 이벤트에 직접 지정된 시간대를 우선 적용하고, 없으면 fallback 시간대를 사용한다.")
    void givenExplicitAndMissingBoundaryZones_whenNormalizePageSchedule_thenResolvesSingleZone() {
        // when
        NormalizedEventSchedule result = normalizer.normalizeSchedule(
                new GoogleCalendarEventTimeResponse(
                        null,
                        "2026-07-20T09:00:00+09:00",
                        "Asia/Seoul"
                ),
                new GoogleCalendarEventTimeResponse(
                        null,
                        "2026-07-20T10:00:00+09:00",
                        null
                ),
                "Asia/Seoul"
        );

        // then
        assertThat(result.startAt()).isEqualTo(Instant.parse("2026-07-20T00:00:00Z"));
        assertThat(result.endAt()).isEqualTo(Instant.parse("2026-07-20T01:00:00Z"));
        assertThat(result.timeZone()).isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("오프셋 정보가 없는 중복 시간대 일정은 페이지 타임존 기준 가장 빠른 오프셋을 우선 사용한다")
    void givenOffsetlessOverlapAndPageZone_whenNormalizePageSchedule_thenUsesEarlierOffset() {
        // when
        NormalizedEventSchedule result = normalizer.normalizeSchedule(
                new GoogleCalendarEventTimeResponse(null, "2026-11-01T01:30:00", null),
                new GoogleCalendarEventTimeResponse(null, "2026-11-01T02:30:00", null),
                "America/New_York"
        );

        // then
        assertThat(result.startAt()).isEqualTo(Instant.parse("2026-11-01T05:30:00Z"));
        assertThat(result.endAt()).isEqualTo(Instant.parse("2026-11-01T07:30:00Z"));
        assertThat(result.timeZone()).isEqualTo("America/New_York");
    }

    @Test
    @DisplayName("page timezone fallback 요청 시 zone mismatch, invalid zone, offset mismatch 이면 예외를 반환한다")
    void givenInvalidPageTimeZoneResolution_whenNormalizePageSchedule_thenRejectsProviderResponse() {
        GoogleCalendarEventTimeResponse explicitUtc =
                new GoogleCalendarEventTimeResponse(null, "2026-07-20T09:00:00Z", "UTC");
        GoogleCalendarEventTimeResponse fallbackBoundary =
                new GoogleCalendarEventTimeResponse(null, "2026-07-20T10:00:00+09:00", null);

        assertInvalidResponse(() -> normalizer.normalizeSchedule(
                explicitUtc,
                fallbackBoundary,
                "Asia/Seoul"
        ));
        assertInvalidResponse(() -> normalizer.normalizeSchedule(
                new GoogleCalendarEventTimeResponse(null, "2026-07-20T09:00:00", null),
                new GoogleCalendarEventTimeResponse(null, "2026-07-20T10:00:00", null),
                "Invalid/Zone"
        ));
        assertInvalidResponse(() -> normalizer.normalizeSchedule(
                new GoogleCalendarEventTimeResponse(null, "2026-07-20T09:00:00Z", null),
                new GoogleCalendarEventTimeResponse(null, "2026-07-20T10:00:00Z", null),
                "Asia/Seoul"
        ));
    }

    @Test
    @DisplayName("all-day 일정은 시작일과 종료일을 각각 UTC 자정으로 변환한다")
    void givenAllDayRange_whenNormalizeSchedule_thenPreservesExclusiveEnd() {
        // when
        NormalizedEventSchedule result = normalizer.normalizeSchedule(
                new GoogleCalendarEventTimeResponse("2026-07-20", null, null),
                new GoogleCalendarEventTimeResponse("2026-07-23", null, null)
        );

        // then
        assertThat(result.startAt()).isEqualTo(Instant.parse("2026-07-20T00:00:00Z"));
        assertThat(result.endAt()).isEqualTo(Instant.parse("2026-07-23T00:00:00Z"));
        assertThat(result.allDay()).isTrue();
    }

    @Test
    @DisplayName("date/dateTime 두 가지 모두 있는 경우, offset-zone 불일치 및 DST 차이는 invalid response 예외를 반환한다")
    void givenKnownInvalidTimes_whenNormalize_thenReturnsProviderInvalidResponse() {
        assertInvalidResponse(() -> normalizer.normalize(
                new GoogleCalendarEventTimeResponse(
                        "2026-07-20",
                        "2026-07-20T09:00:00+09:00",
                        "Asia/Seoul"
                )
        ));
        assertInvalidResponse(() -> normalizer.normalize(
                new GoogleCalendarEventTimeResponse(
                        null,
                        "2026-07-20T09:00:00Z",
                        "Asia/Seoul"
                )
        ));
        assertInvalidResponse(() -> normalizer.normalize(
                new GoogleCalendarEventTimeResponse(
                        null,
                        "2026-03-08T02:30:00",
                        "America/New_York"
                )
        ));
    }

    @Test
    @DisplayName("date와 dateTime이 모두 없으면 invalid response 예외를 반환한다")
    void givenEmptyTimeUnion_whenNormalize_thenReturnsProviderInvalidResponse() {
        assertInvalidResponse(() -> normalizer.normalize(
                new GoogleCalendarEventTimeResponse(null, null, null)
        ));
    }

    @Test
    @DisplayName("offset이 있는 timed 값은 timezone 없이 제공된 offset으로 정규화한다")
    void givenOffsetTimedValueWithoutTimeZone_whenNormalize_thenUsesSuppliedOffset() {
        // when
        NormalizedEventTime result = normalizer.normalize(
                new GoogleCalendarEventTimeResponse(null, "2026-07-20T09:00:00Z", null)
        );

        // then
        assertThat(result.instant()).isEqualTo(Instant.parse("2026-07-20T09:00:00Z"));
        assertThat(result.allDay()).isFalse();
        assertThat(result.timeZone()).isNull();
    }

    @Test
    @DisplayName("offset이 없는 timed 값에 timezone이 없으면 invalid response 예외를 반환한다")
    void givenOffsetlessTimedValueWithoutTimeZone_whenNormalize_thenReturnsProviderInvalidResponse() {
        assertInvalidResponse(() -> normalizer.normalize(
                new GoogleCalendarEventTimeResponse(null, "2026-07-20T09:00:00", null)
        ));
    }

    @Test
    @DisplayName("유효하지 않은 IANA timezone은 invalid response를 반환한다")
    void givenInvalidIanaTimeZone_whenNormalize_thenReturnsProviderInvalidResponse() {
        assertInvalidResponse(() -> normalizer.normalize(
                new GoogleCalendarEventTimeResponse(
                        null,
                        "2026-07-20T09:00:00",
                        "Invalid/TimeZone"
                )
        ));
    }

    @Test
    @DisplayName("endAt이 startAt과 같거나 이전이면 invalid response 예외를 반환한다")
    void givenNonIncreasingSchedule_whenNormalizeSchedule_thenReturnsProviderInvalidResponse() {
        GoogleCalendarEventTimeResponse start =
                new GoogleCalendarEventTimeResponse("2026-07-20", null, null);

        assertInvalidResponse(() -> normalizer.normalizeSchedule(
                start,
                new GoogleCalendarEventTimeResponse("2026-07-20", null, null)
        ));
        assertInvalidResponse(() -> normalizer.normalizeSchedule(
                start,
                new GoogleCalendarEventTimeResponse("2026-07-19", null, null)
        ));
    }

    private void assertInvalidResponse(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID));
    }
}
