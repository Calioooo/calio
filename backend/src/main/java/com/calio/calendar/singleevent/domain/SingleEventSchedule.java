package com.calio.calendar.singleevent.domain;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.common.time.IanaTimeZones;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;

@Embeddable
public record SingleEventSchedule(
    @Column(name = "start_at", nullable = false) Instant startAt,
    @Column(name = "end_at", nullable = false) Instant endAt,
    @Column(name = "all_day", nullable = false) boolean allDay,
    @Column(name = "time_zone") String timeZone) {

  public SingleEventSchedule {
    if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
      throw new CalioException(ErrorCode.INVALID_TIME_RANGE);
    }
    if (allDay) {
      if (timeZone != null || !isUtcMidnight(startAt) || !isUtcMidnight(endAt)) {
        throw new CalioException(ErrorCode.INVALID_ALL_DAY_SCHEDULE);
      }
      timeZone = null;
    } else if (timeZone == null || timeZone.isBlank() || !IanaTimeZones.contains(timeZone)) {
      throw new CalioException(ErrorCode.INVALID_TIME_ZONE);
    }
  }

  private static boolean isUtcMidnight(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC).toLocalTime().equals(LocalTime.MIDNIGHT);
  }
}
