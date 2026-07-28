package com.calio.calendar.external.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer.NormalizedEventSchedule;
import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer.NormalizedEventTime;
import com.calio.calendar.external.google.dto.GoogleCalendarEventTime;
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
                    new GoogleCalendarEventTime("2026-07-20", null, "Asia/Seoul")
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
    @DisplayName("offsetless overlap 시각은 IANA zone의 earlier offset을 선택한다")
    void givenOffsetlessOverlap_whenNormalize_thenUsesEarlierOffset() {
        // when
        NormalizedEventTime result = normalizer.normalize(
                new GoogleCalendarEventTime(
                        null,
                        "2026-11-01T01:30:00",
                        "America/New_York"
                )
        );

        // then
        assertThat(result.instant()).isEqualTo(Instant.parse("2026-11-01T05:30:00Z"));
    }

    @Test
    @DisplayName("offset과 IANA zone rule이 일치하면 supplied offset으로 Instant를 계산한다")
    void givenMatchingOffsetAndZone_whenNormalize_thenUsesSuppliedOffset() {
        // when
        NormalizedEventTime result = normalizer.normalize(
                new GoogleCalendarEventTime(
                        null,
                        "2026-07-20T09:00:00+09:00",
                        "Asia/Seoul"
                )
        );

        // then
        assertThat(result.instant()).isEqualTo(Instant.parse("2026-07-20T00:00:00Z"));
    }

    @Test
    @DisplayName("active timed schedule은 동일한 명시적 IANA timezone을 요구한다")
    void givenDifferentTimedZones_whenNormalizeSchedule_thenRejectsProviderResponse() {
        // when, then
        assertInvalidResponse(() -> normalizer.normalizeSchedule(
                new GoogleCalendarEventTime(
                        null,
                        "2026-07-20T09:00:00+09:00",
                        "Asia/Seoul"
                ),
                new GoogleCalendarEventTime(
                        null,
                        "2026-07-20T01:00:00Z",
                        "UTC"
                )
        ));
    }

    @Test
    @DisplayName("all-day exclusive end는 start와 별도로 같은 UTC identity 규칙을 적용한다")
    void givenAllDayRange_whenNormalizeSchedule_thenPreservesExclusiveEnd() {
        // when
        NormalizedEventSchedule result = normalizer.normalizeSchedule(
                new GoogleCalendarEventTime("2026-07-20", null, null),
                new GoogleCalendarEventTime("2026-07-23", null, null)
        );

        // then
        assertThat(result.startAt()).isEqualTo(Instant.parse("2026-07-20T00:00:00Z"));
        assertThat(result.endAt()).isEqualTo(Instant.parse("2026-07-23T00:00:00Z"));
        assertThat(result.allDay()).isTrue();
    }

    @Test
    @DisplayName("date/dateTime union, offset-zone mismatch와 DST gap은 invalid response다")
    void givenKnownInvalidTimes_whenNormalize_thenReturnsProviderInvalidResponse() {
        assertInvalidResponse(() -> normalizer.normalize(
                new GoogleCalendarEventTime(
                        "2026-07-20",
                        "2026-07-20T09:00:00+09:00",
                        "Asia/Seoul"
                )
        ));
        assertInvalidResponse(() -> normalizer.normalize(
                new GoogleCalendarEventTime(
                        null,
                        "2026-07-20T09:00:00Z",
                        "Asia/Seoul"
                )
        ));
        assertInvalidResponse(() -> normalizer.normalize(
                new GoogleCalendarEventTime(
                        null,
                        "2026-03-08T02:30:00",
                        "America/New_York"
                )
        ));
    }

    private void assertInvalidResponse(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID));
    }
}
