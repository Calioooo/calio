package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer;
import com.calio.calendar.external.google.dto.GoogleCalendarEventItem;
import com.calio.calendar.external.google.dto.GoogleCalendarEventTime;
import com.calio.calendar.integration.service.GoogleCalendarRecurrenceMapper.ActiveExceptionResult;
import com.calio.calendar.integration.service.GoogleCalendarRecurrenceMapper.CancelledExceptionResult;
import com.calio.calendar.integration.service.GoogleCalendarRecurrenceMapper.MasterResult;
import com.calio.calendar.recurrence.service.Ical4jRecurrenceEngine;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarRecurrenceMapperTest {

    private final GoogleCalendarRecurrenceMapper mapper = new GoogleCalendarRecurrenceMapper(
            new GoogleCalendarEventTimeNormalizer(),
            new Ical4jRecurrenceEngine()
    );

    @Test
    @DisplayName("recurring master는 canonical title, schedule과 engine-normalized recurrence를 만든다")
    void givenRecurringMaster_whenMap_thenReturnsPersistenceIndependentCanonicalInput() {
        // given
        GoogleCalendarEventItem item = item(
                "master-id",
                "confirmed",
                " ",
                "description",
                List.of(
                        "EXDATE;TZID=Asia/Seoul:20260722T090000",
                        "RRULE:FREQ=DAILY;COUNT=3"
                ),
                null,
                null,
                timed("2026-07-20T09:00:00+09:00", "Asia/Seoul"),
                timed("2026-07-20T10:00:00+09:00", "Asia/Seoul")
        );

        // when
        MasterResult result = mapper.mapMaster(item);

        // then
        assertThat(result.externalEventId()).isEqualTo("master-id");
        assertThat(result.title()).isEqualTo("(제목 없음)");
        assertThat(result.description()).isEqualTo("description");
        assertThat(result.schedule().startAt()).isEqualTo(Instant.parse("2026-07-20T00:00:00Z"));
        assertThat(result.recurrenceRules()).containsExactly(
                "RRULE:FREQ=DAILY;COUNT=3",
                "EXDATE;TZID=Asia/Seoul:20260722T090000"
        );
    }

    @Test
    @DisplayName("active exception은 original identity와 cross-type final snapshot을 독립 계산한다")
    void givenAllDayOriginAndTimedFinal_whenMapException_thenPreservesBothShapes() {
        // given
        GoogleCalendarEventItem item = item(
                "exception-id",
                "confirmed",
                null,
                null,
                List.of(),
                "master-id",
                new GoogleCalendarEventTime("2026-07-21", null, "ignored/provider-zone"),
                timed("2026-07-21T15:00:00+09:00", "Asia/Seoul"),
                timed("2026-07-21T16:00:00+09:00", "Asia/Seoul")
        );

        // when
        ActiveExceptionResult result = (ActiveExceptionResult) mapper.mapException(item);

        // then
        assertThat(result.originStartAt()).isEqualTo(Instant.parse("2026-07-21T00:00:00Z"));
        assertThat(result.title()).isEqualTo("(제목 없음)");
        assertThat(result.description()).isNull();
        assertThat(result.schedule().allDay()).isFalse();
        assertThat(result.schedule().startAt()).isEqualTo(Instant.parse("2026-07-21T06:00:00Z"));
    }

    @Test
    @DisplayName("cancelled minimum exception은 identity만으로 mapping result를 만든다")
    void givenCancelledMinimumException_whenMapException_thenDoesNotRequireFinalSchedule() {
        // given
        GoogleCalendarEventItem item = item(
                "exception-id",
                "cancelled",
                null,
                null,
                List.of(),
                "master-id",
                timed("2026-07-21T09:00:00+09:00", null),
                null,
                null
        );

        // when
        CancelledExceptionResult result = (CancelledExceptionResult) mapper.mapException(item);

        // then
        assertThat(result.externalEventId()).isEqualTo("exception-id");
        assertThat(result.parentExternalEventId()).isEqualTo("master-id");
        assertThat(result.originStartAt()).isEqualTo(Instant.parse("2026-07-21T00:00:00Z"));
    }

    @Test
    @DisplayName("provider recurrence validation 실패는 Google invalid response로 변환한다")
    void givenInvalidProviderRecurrence_whenMapMaster_thenTranslatesOnlyKnownFailure() {
        // given
        GoogleCalendarEventItem item = item(
                "master-id",
                "confirmed",
                "Title",
                null,
                List.of("RRULE:NOT-A-RULE"),
                null,
                null,
                timed("2026-07-20T09:00:00+09:00", "Asia/Seoul"),
                timed("2026-07-20T10:00:00+09:00", "Asia/Seoul")
        );

        // when, then
        assertThatThrownBy(() -> mapper.mapMaster(item))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID));
    }

    private GoogleCalendarEventItem item(
            String id,
            String status,
            String summary,
            String description,
            List<String> recurrence,
            String recurringEventId,
            GoogleCalendarEventTime originalStartTime,
            GoogleCalendarEventTime start,
            GoogleCalendarEventTime end
    ) {
        return new GoogleCalendarEventItem(
                id,
                status,
                "\"etag\"",
                Instant.parse("2026-07-01T00:00:00Z"),
                summary,
                description,
                recurrence,
                recurringEventId,
                originalStartTime,
                start,
                end
        );
    }

    private GoogleCalendarEventTime timed(String dateTime, String timeZone) {
        return new GoogleCalendarEventTime(null, dateTime, timeZone);
    }
}
