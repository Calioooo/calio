package com.calio.calendar.aicalendar.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.aicalendar.config.CalendarAIProperties;
import com.calio.calendar.aicalendar.service.CalendarAgentObservationService;
import com.calio.calendar.aicalendar.service.tool.dto.CalendarLookupToolRequest;
import com.calio.calendar.aicalendar.service.tool.dto.FreeTimeSearchToolRequest;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.service.EventService;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CalendarAgentToolsTest {

    @Mock
    private EventService eventService;

    @Mock
    private CalendarAgentObservationService observationService;

    @Test
    @DisplayName("agenda tool은 기존 Calendar 조회 결과에서 description을 명시 요청한 경우에만 노출한다")
    void givenCalendarEvents_whenLookupWithoutDetails_thenHidesDescription() {
        // given
        when(eventService.listEvents(any(), any(), any())).thenReturn(List.of(timedEvent()));
        CalendarAgentTools tools = tools();

        // when
        var result = tools.lookupCalendarEvents(new CalendarLookupToolRequest(
                "2026-07-01",
                "2026-07-01",
                false
        ));

        // then
        assertThat(result.events()).singleElement().satisfies(event -> {
            assertThat(event.title()).isEqualTo("Planning");
            assertThat(event.description()).isNull();
            assertThat(event.allDay()).isFalse();
        });
        verify(eventService).listEvents(any(), any(), any());
    }

    @Test
    @DisplayName("free-time tool은 timed event만 half-open interval로 차단하고 all-day event는 notice로 남긴다")
    void givenTimedAndAllDayEvents_whenFindFreeTime_thenReturnsFreeSlotsWithAllDayNotice() {
        // given
        when(eventService.listEvents(any(), any(), any())).thenReturn(List.of(
                timedEvent(),
                allDayEvent()
        ));
        CalendarAgentTools tools = tools();

        // when
        var result = tools.findCalendarFreeTime(new FreeTimeSearchToolRequest(
                "2026-07-01",
                "2026-07-01",
                "09:00",
                "12:00",
                60
        ));

        // then
        assertThat(result.freeTimes()).hasSize(2);
        assertThat(result.freeTimes().getFirst().start()).isEqualTo("2026-07-01T09:00:00Z");
        assertThat(result.freeTimes().getFirst().end()).isEqualTo("2026-07-01T10:00:00Z");
        assertThat(result.freeTimes().getFirst().allDayNotices()).containsExactly("Holiday");
        assertThat(result.freeTimes().get(1).start()).isEqualTo("2026-07-01T11:00:00Z");
        assertThat(result.freeTimes().get(1).end()).isEqualTo("2026-07-01T12:00:00Z");
    }

    private CalendarAgentTools tools() {
        return new CalendarAgentTools(
                1L,
                "conversation-id",
                ZoneId.of("UTC"),
                eventService,
                new CalendarAIProperties(),
                observationService
        );
    }

    private EventResponse timedEvent() {
        return new EventResponse(
                1L,
                "Planning",
                "Planning details",
                Instant.parse("2026-07-01T10:00:00Z"),
                Instant.parse("2026-07-01T11:00:00Z"),
                false,
                "UTC",
                false,
                null,
                false,
                null,
                null,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z")
        );
    }

    private EventResponse allDayEvent() {
        return new EventResponse(
                2L,
                "Holiday",
                "Holiday details",
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-02T00:00:00Z"),
                true,
                null,
                false,
                null,
                false,
                null,
                null,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z")
        );
    }
}
