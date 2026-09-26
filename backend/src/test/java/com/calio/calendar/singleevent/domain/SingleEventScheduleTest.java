package com.calio.calendar.singleevent.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SingleEventScheduleTest {

  @Test
  @DisplayName("종일 일정은 UTC 자정 경계와 null timezone으로 정규화한다")
  void givenAllDaySchedule_whenCreate_thenNormalizesTimeZone() {
    SingleEventSchedule schedule =
        new SingleEventSchedule(
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-02T00:00:00Z"),
            true,
            null);

    assertThat(schedule.timeZone()).isNull();
  }

  @Test
  @DisplayName("시간 지정 일정은 유효한 IANA timezone이 필요하다")
  void givenTimedScheduleWithoutTimeZone_whenCreate_thenRejectsSchedule() {
    assertThatThrownBy(
            () ->
                new SingleEventSchedule(
                    Instant.parse("2026-01-01T09:00:00Z"),
                    Instant.parse("2026-01-01T10:00:00Z"),
                    false,
                    null))
        .isInstanceOf(CalioException.class)
        .extracting(exception -> ((CalioException) exception).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_TIME_ZONE);
  }
}
