package com.calio.calendar.singleevent.usecase;

import com.calio.calendar.singleevent.controller.dto.EventResponse;
import com.calio.calendar.singleevent.service.dto.CalendarFreeTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class CalendarAvailabilityCalculator {

  List<CalendarFreeTime> find(
      List<EventResponse> events,
      LocalDate startDate,
      LocalDate endDate,
      ZoneId timeZone,
      LocalTime availableFrom,
      LocalTime availableUntil,
      Duration minimumDuration) {
    List<CalendarFreeTime> availableTimes = new ArrayList<>();
    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
      addAvailableTimesForDate(
          events, date, timeZone, availableFrom, availableUntil, minimumDuration, availableTimes);
    }
    return availableTimes;
  }

  private void addAvailableTimesForDate(
      List<EventResponse> events,
      LocalDate date,
      ZoneId timeZone,
      LocalTime availableFrom,
      LocalTime availableUntil,
      Duration minimumDuration,
      List<CalendarFreeTime> availableTimes) {
    Instant windowStart = LocalDateTime.of(date, availableFrom).atZone(timeZone).toInstant();
    Instant windowEnd = LocalDateTime.of(date, availableUntil).atZone(timeZone).toInstant();
    List<TimeRange> occupiedRanges =
        events.stream()
            .filter(event -> !event.allDay())
            .map(
                event ->
                    TimeRange.overlapping(event.startAt(), event.endAt(), windowStart, windowEnd))
            .filter(TimeRange::hasDuration)
            .sorted(Comparator.comparing(TimeRange::start))
            .toList();
    List<String> allDayTitles =
        events.stream()
            .filter(EventResponse::allDay)
            .filter(event -> occursOnDate(event, date))
            .map(EventResponse::title)
            .toList();
    Instant availableStart = windowStart;
    for (TimeRange occupiedRange : occupiedRanges) {
      addAvailableTime(
          availableStart,
          occupiedRange.start(),
          minimumDuration,
          allDayTitles,
          timeZone,
          availableTimes);
      if (occupiedRange.end().isAfter(availableStart)) {
        availableStart = occupiedRange.end();
      }
    }
    addAvailableTime(
        availableStart, windowEnd, minimumDuration, allDayTitles, timeZone, availableTimes);
  }

  private void addAvailableTime(
      Instant start,
      Instant end,
      Duration minimumDuration,
      List<String> allDayTitles,
      ZoneId timeZone,
      List<CalendarFreeTime> availableTimes) {
    if (Duration.between(start, end).compareTo(minimumDuration) >= 0) {
      availableTimes.add(
          new CalendarFreeTime(
              start.atZone(timeZone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
              end.atZone(timeZone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
              allDayTitles));
    }
  }

  private boolean occursOnDate(EventResponse event, LocalDate date) {
    LocalDate startDate = event.startAt().atZone(ZoneOffset.UTC).toLocalDate();
    LocalDate endDate = event.endAt().atZone(ZoneOffset.UTC).toLocalDate();
    return !date.isBefore(startDate) && date.isBefore(endDate);
  }

  private record TimeRange(Instant start, Instant end) {
    private static TimeRange overlapping(
        Instant start, Instant end, Instant rangeStart, Instant rangeEnd) {
      Instant overlapStart = start.isAfter(rangeStart) ? start : rangeStart;
      Instant overlapEnd = end.isBefore(rangeEnd) ? end : rangeEnd;
      return new TimeRange(overlapStart, overlapEnd);
    }

    private boolean hasDuration() {
      return start.isBefore(end);
    }
  }
}
