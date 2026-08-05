package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer;
import com.calio.calendar.external.google.dto.GoogleCalendarEventItem;
import com.calio.calendar.external.google.dto.GoogleCalendarEventTime;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.ActiveRecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.CancelledRecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceEventUpsert;
import com.calio.calendar.recurrence.service.Ical4jRecurrenceEngine;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
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
    @DisplayName("recurrence event는 canonical title, schedule과 engine-normalized recurrence를 만든다")
    void givenRecurrenceEvent_whenMap_thenReturnsPersistenceIndependentCanonicalInput() {
        // given
        GoogleCalendarEventItem item = item(
                "recurrence-event-id",
                "confirmed",
                " ",
                "description",
                List.of(
                        "EXDATE;TZID=Asia/Seoul:20260722T090000",
                        "RDATE;TZID=Asia/Seoul:20260725T090000",
                        "EXRULE:FREQ=WEEKLY;BYDAY=TU",
                        "RRULE:FREQ=DAILY;COUNT=3"
                ),
                null,
                null,
                timed("2026-07-20T09:00:00+09:00", "Asia/Seoul"),
                timed("2026-07-20T10:00:00+09:00", "Asia/Seoul")
        );

        // when
        RecurrenceEventUpsert recurrenceEvent = mapper.mapRecurrenceEvent(item);

        // then
        assertThat(recurrenceEvent.externalEventId()).isEqualTo("recurrence-event-id");
        assertThat(recurrenceEvent.title()).isEqualTo("(제목 없음)");
        assertThat(recurrenceEvent.description()).isEqualTo("description");
        assertThat(recurrenceEvent.schedule().startAt())
                .isEqualTo(Instant.parse("2026-07-20T00:00:00Z"));
        assertThat(recurrenceEvent.recurrenceRules()).containsExactly(
                "RRULE:FREQ=DAILY;COUNT=3",
                "RDATE;TZID=Asia/Seoul:20260725T090000",
                "EXDATE;TZID=Asia/Seoul:20260722T090000",
                "EXRULE:FREQ=WEEKLY;BYDAY=TU"
        );
    }

    @Test
    @DisplayName("active recurrence override는 original identity와 cross-type final snapshot을 독립 계산한다")
    void givenAllDayOriginAndTimedFinal_whenMapRecurrenceOverride_thenPreservesBothShapes() {
        // given
        GoogleCalendarEventItem item = item(
                "recurrence-override-id",
                "confirmed",
                null,
                null,
                List.of(),
                "recurrence-event-id",
                new GoogleCalendarEventTime("2026-07-21", null, "ignored/provider-zone"),
                timed("2026-07-21T15:00:00+09:00", "Asia/Seoul"),
                timed("2026-07-21T16:00:00+09:00", "Asia/Seoul")
        );

        // when
        ActiveRecurrenceEventOverrideUpsert override =
                (ActiveRecurrenceEventOverrideUpsert) mapper.mapRecurrenceOverride(item);

        // then
        assertThat(override.originStartAt()).isEqualTo(Instant.parse("2026-07-21T00:00:00Z"));
        assertThat(override.title()).isEqualTo("(제목 없음)");
        assertThat(override.description()).isNull();
        assertThat(override.schedule().allDay()).isFalse();
        assertThat(override.schedule().startAt())
                .isEqualTo(Instant.parse("2026-07-21T06:00:00Z"));
    }

    @Test
    @DisplayName("timed origin과 all-day final schedule을 독립적으로 정규화한다")
    void givenTimedOriginAndAllDayFinal_whenMapRecurrenceOverride_thenPreservesBothShapes() {
        // given
        GoogleCalendarEventItem item = item(
                "recurrence-override-id",
                "confirmed",
                "All-day override",
                null,
                List.of(),
                "recurrence-event-id",
                timed("2026-07-21T09:00:00+09:00", null),
                new GoogleCalendarEventTime("2026-07-22", null, "ignored/provider-zone"),
                new GoogleCalendarEventTime("2026-07-24", null, "ignored/provider-zone")
        );

        // when
        ActiveRecurrenceEventOverrideUpsert override =
                (ActiveRecurrenceEventOverrideUpsert) mapper.mapRecurrenceOverride(item);

        // then
        assertThat(override.originStartAt()).isEqualTo(Instant.parse("2026-07-21T00:00:00Z"));
        assertThat(override.schedule().allDay()).isTrue();
        assertThat(override.schedule().timeZone()).isNull();
        assertThat(override.schedule().startAt())
                .isEqualTo(Instant.parse("2026-07-22T00:00:00Z"));
        assertThat(override.schedule().endAt())
                .isEqualTo(Instant.parse("2026-07-24T00:00:00Z"));
    }

    @Test
    @DisplayName("cancelled minimum recurrence override는 식별 정보만으로 정규화한다")
    void givenCancelledMinimumRecurrenceOverride_whenMap_thenDoesNotRequireFinalSchedule() {
        // given
        GoogleCalendarEventItem item = item(
                "recurrence-override-id",
                "cancelled",
                null,
                null,
                List.of(),
                "recurrence-event-id",
                timed("2026-07-21T09:00:00+09:00", null),
                null,
                null
        );

        // when
        CancelledRecurrenceEventOverrideUpsert override =
                (CancelledRecurrenceEventOverrideUpsert) mapper.mapRecurrenceOverride(item);

        // then
        assertThat(override.externalEventId()).isEqualTo("recurrence-override-id");
        assertThat(override.recurrenceEventExternalId()).isEqualTo("recurrence-event-id");
        assertThat(override.originStartAt()).isEqualTo(Instant.parse("2026-07-21T00:00:00Z"));
    }

    @Test
    @DisplayName("provider recurrence validation 실패는 Google invalid response로 변환한다")
    void givenInvalidProviderRecurrence_whenMapRecurrenceEvent_thenTranslatesOnlyKnownFailure() {
        // given
        GoogleCalendarEventItem item = item(
                "recurrence-event-id",
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
        assertThatThrownBy(() -> mapper.mapRecurrenceEvent(item))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID));
    }

    @Test
    @DisplayName("recurrence event와 override identity가 혼합된 provider item은 invalid response다")
    void givenMixedRecurrenceEventAndOverride_whenMapOverride_thenReturnsProviderInvalidResponse() {
        // given
        GoogleCalendarEventItem item = item(
                "recurrence-override-id",
                "confirmed",
                "Invalid mixed item",
                null,
                List.of("RRULE:FREQ=DAILY"),
                "recurrence-event-id",
                timed("2026-07-21T09:00:00+09:00", null),
                timed("2026-07-21T10:00:00+09:00", "Asia/Seoul"),
                timed("2026-07-21T11:00:00+09:00", "Asia/Seoul")
        );

        // when, then
        assertInvalidResponse(() -> mapper.mapRecurrenceOverride(item));
    }

    @Test
    @DisplayName("recurrence override의 recurrence-event ID 또는 originalStartTime 누락은 invalid response다")
    void givenMissingRecurrenceOverrideIdentity_whenMap_thenReturnsProviderInvalidResponse() {
        GoogleCalendarEventTime start = timed(
                "2026-07-21T10:00:00+09:00",
                "Asia/Seoul"
        );
        GoogleCalendarEventTime end = timed(
                "2026-07-21T11:00:00+09:00",
                "Asia/Seoul"
        );
        GoogleCalendarEventItem missingRecurrenceEventId = item(
                "recurrence-override-id",
                "confirmed",
                "Override",
                null,
                List.of(),
                null,
                timed("2026-07-21T09:00:00+09:00", null),
                start,
                end
        );
        GoogleCalendarEventItem missingOriginalStartTime = item(
                "recurrence-override-id",
                "confirmed",
                "Override",
                null,
                List.of(),
                "recurrence-event-id",
                null,
                start,
                end
        );

        assertInvalidResponse(() -> mapper.mapRecurrenceOverride(missingRecurrenceEventId));
        assertInvalidResponse(() -> mapper.mapRecurrenceOverride(missingOriginalStartTime));
    }

    @Test
    @DisplayName("예상하지 못한 recurrence engine 오류는 Google invalid response로 변환하지 않는다")
    void givenUnexpectedEngineFailure_whenMapRecurrenceEvent_thenPropagatesOriginalFailure() {
        // given
        IllegalStateException failure = new IllegalStateException("unexpected engine failure");
        GoogleCalendarRecurrenceMapper failingMapper = new GoogleCalendarRecurrenceMapper(
                new GoogleCalendarEventTimeNormalizer(),
                failingEngine(failure)
        );
        GoogleCalendarEventItem item = item(
                "recurrence-event-id",
                "confirmed",
                "Title",
                null,
                List.of("RRULE:FREQ=DAILY"),
                null,
                null,
                timed("2026-07-20T09:00:00+09:00", "Asia/Seoul"),
                timed("2026-07-20T10:00:00+09:00", "Asia/Seoul")
        );

        // when, then
        assertThatThrownBy(() -> failingMapper.mapRecurrenceEvent(item)).isSameAs(failure);
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

    private void assertInvalidResponse(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID));
    }

    private Rfc5545RecurrenceEngine failingEngine(RuntimeException failure) {
        return new Rfc5545RecurrenceEngine() {
            @Override
            public List<String> validate(
                    RecurrenceSchedule schedule,
                    List<String> recurrenceRules
            ) {
                throw failure;
            }

            @Override
            public List<RecurrenceOccurrence> expand(
                    RecurrenceSchedule schedule,
                    List<String> recurrenceRules,
                    Instant from,
                    Instant to
            ) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean containsOrigin(
                    RecurrenceSchedule schedule,
                    List<String> recurrenceRules,
                    Instant originStartAt
            ) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
