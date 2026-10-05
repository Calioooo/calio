package com.calio.calendar.recurrence.domain;

import com.calio.calendar.common.domain.CanonicalSchedule;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

/** 회차 변경 시간의 의미와 유효성을 보존하는 불변 값. */
@Embeddable
public record RecurrenceOverrideSchedule(
    @Column(name = "override_start_at") Instant startAt,
    @Column(name = "override_end_at") Instant endAt,
    @Column(name = "override_all_day") boolean allDay,
    @Column(name = "override_time_zone") String timeZone) {
  public RecurrenceOverrideSchedule {
    CanonicalSchedule.recurrenceOverride(startAt, endAt, allDay, timeZone);
  }

  public static RecurrenceOverrideSchedule from(CanonicalSchedule schedule) {
    return new RecurrenceOverrideSchedule(
        schedule.startAt(), schedule.endAt(), schedule.allDay(), schedule.timeZone());
  }
}
