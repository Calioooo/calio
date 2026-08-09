package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.dto.GoogleCalendarEventResponse;
import com.calio.calendar.external.google.dto.GoogleCalendarEventTimeResponse;
import com.calio.calendar.external.google.service.dto.NormalizedEventSchedule;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage.RecurrenceEventUpsert;
import com.calio.calendar.integration.service.dto.GoogleCalendarRecurrenceEventLookup.FoundRecurrenceEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarRecurrenceEventLoaderTest {

    private final GoogleCalendarEventRequestService eventRequestService =
            mock(GoogleCalendarEventRequestService.class);
    private final GoogleCalendarRecurrenceMapper recurrenceMapper =
            mock(GoogleCalendarRecurrenceMapper.class);
    private final GoogleCalendarRecurrenceEventLoader loader =
            new GoogleCalendarRecurrenceEventLoader(eventRequestService, recurrenceMapper);

    @Test
    @DisplayName("이미 조회한 recurrence-event는 Google에 다시 요청하지 않는다")
    void givenCachedRecurrenceEvent_whenLoad_thenReturnsCachedEvent() {
        // given
        GoogleCalendarSyncRunContext context = new GoogleCalendarSyncRunContext("token");
        RecurrenceEventUpsert expected = recurrenceEventUpsert("recurrence-event-1");
        context.rememberRecurrenceEvent(
                "recurrence-event-1",
                new FoundRecurrenceEvent(expected)
        );

        // when
        Optional<RecurrenceEventUpsert> actual = loader.loadRecurrenceEvent(
                10L,
                "recurrence-event-1",
                context
        );

        // then
        assertThat(actual).containsSame(expected);
        verifyNoInteractions(eventRequestService);
    }

    @Test
    @DisplayName("존재하지 않는 recurrence-event는 cache 하여 동일한 조회 결과를 유지한다")
    void givenMissingRecurrenceEvent_whenLoadedAgain_thenKeepsMissingResult(){
        // given
        GoogleCalendarSyncRunContext context = new GoogleCalendarSyncRunContext("token");
        when(eventRequestService.getEvent(10L, "recurrence-event-1", context))
                .thenReturn(Optional.empty());

        // when
        Optional<RecurrenceEventUpsert> first = loader.loadRecurrenceEvent(
                10L, "recurrence-event-1", context
        );
        Optional<RecurrenceEventUpsert> second = loader.loadRecurrenceEvent(
                10L, "recurrence-event-1", context
        );

        // then
        assertThat(first).isEmpty();
        assertThat(second).isEmpty();
        verify(eventRequestService).getEvent(10L, "recurrence-event-1", context);
    }

    @Test
    @DisplayName("유효한 recurrence-event 응답은 cache 하여 동일한 조회 결과를 유지한다")
    void givenValidRecurrenceEvent_whenLoadTwice_thenMapsAndCachesOnce() {
        // given
        GoogleCalendarSyncRunContext context = new GoogleCalendarSyncRunContext("token");
        GoogleCalendarEventResponse response = recurrenceEvent("recurrence-event-1");
        RecurrenceEventUpsert expected = recurrenceEventUpsert("recurrence-event-1");
        when(eventRequestService.getEvent(10L, "recurrence-event-1", context))
                .thenReturn(Optional.of(response));
        when(recurrenceMapper.mapRecurrenceEvent(response)).thenReturn(expected);

        // when
        Optional<RecurrenceEventUpsert> first = loader.loadRecurrenceEvent(
                10L, "recurrence-event-1", context
        );
        Optional<RecurrenceEventUpsert> second = loader.loadRecurrenceEvent(
                10L, "recurrence-event-1", context
        );

        // then
        assertThat(first).containsSame(expected);
        assertThat(second).containsSame(expected);
        verify(eventRequestService).getEvent(10L, "recurrence-event-1", context);
        verify(recurrenceMapper).mapRecurrenceEvent(response);
    }

    private GoogleCalendarEventResponse recurrenceEvent(String externalEventId) {
        return new GoogleCalendarEventResponse(
                externalEventId,
                "confirmed",
                null,
                null,
                "Daily",
                null,
                List.of("RRULE:FREQ=DAILY"),
                null,
                null,
                new GoogleCalendarEventTimeResponse(null, "2026-07-01T09:00:00Z", "UTC"),
                new GoogleCalendarEventTimeResponse(null, "2026-07-01T10:00:00Z", "UTC")
        );
    }

    private RecurrenceEventUpsert recurrenceEventUpsert(String externalEventId) {
        return new RecurrenceEventUpsert(
                externalEventId,
                null,
                null,
                "Daily",
                null,
                new NormalizedEventSchedule(
                        Instant.parse("2026-07-01T09:00:00Z"),
                        Instant.parse("2026-07-01T10:00:00Z"),
                        false,
                        "UTC"
                ),
                List.of("RRULE:FREQ=DAILY")
        );
    }
}
