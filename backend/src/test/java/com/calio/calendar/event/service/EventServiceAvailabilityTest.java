package com.calio.calendar.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.calio.calendar.event.controller.dto.EventResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventServiceAvailabilityTest {

    @Test
    @DisplayName("timed event는 빈 시간을 차단하고 all-day event는 안내로 포함한다")
    void givenTimedAndAllDayEvents_whenFindAvailableTimes_thenReturnsAvailableTimesWithAllDayNotice() {
        // given
        EventService eventService = mock(EventService.class, CALLS_REAL_METHODS);
        doReturn(List.of(timedEvent(), allDayEvent()))
                .when(eventService)
                .listEvents(any(), any(), any());

        // when
        var availableTimes = eventService.findAvailableTimes(
                1L,
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-01"),
                ZoneId.of("UTC"),
                LocalTime.parse("09:00"),
                LocalTime.parse("12:00"),
                Duration.ofHours(1)
        );

        // then
        assertThat(availableTimes).hasSize(2);
        assertThat(availableTimes.getFirst().start()).isEqualTo("2026-07-01T09:00:00Z");
        assertThat(availableTimes.getFirst().end()).isEqualTo("2026-07-01T10:00:00Z");
        assertThat(availableTimes.getFirst().allDayNotices()).containsExactly("Holiday");
        assertThat(availableTimes.get(1).start()).isEqualTo("2026-07-01T11:00:00Z");
        assertThat(availableTimes.get(1).end()).isEqualTo("2026-07-01T12:00:00Z");
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
