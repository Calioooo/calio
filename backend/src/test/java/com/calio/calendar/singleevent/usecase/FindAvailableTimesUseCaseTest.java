package com.calio.calendar.singleevent.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.singleevent.controller.dto.EventResponse;
import com.calio.calendar.singleevent.service.dto.CalendarFreeTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FindAvailableTimesUseCaseTest {

  @Test
  @DisplayName("시간 일정은 빈 시간을 차단하고 종일 일정은 안내에 포함한다")
  void
      givenTimedAndAllDayEvents_whenFindAvailableTimes_thenReturnsAvailableTimesWithAllDayNotice() {
    List<EventResponse> availableEvents = List.of(timedEvent(), allDayEvent());

    List<CalendarFreeTime> availableTimes =
        FindAvailableTimesUseCase.calculateAvailableTimes(
            availableEvents,
            LocalDate.parse("2026-07-01"),
            LocalDate.parse("2026-07-01"),
            ZoneId.of("UTC"),
            LocalTime.parse("09:00"),
            LocalTime.parse("12:00"),
            Duration.ofHours(1));

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
        Instant.parse("2026-07-01T00:00:00Z"));
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
        Instant.parse("2026-07-01T00:00:00Z"));
  }
}
