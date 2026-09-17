package com.calio.calendar.recurrence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecurrenceScheduleTest {

    @Test
    @DisplayName("영속성 복원과 직접 생성도 factory와 동일한 시간대 규칙을 검증한다")
    void directConstructionCannotBypassScheduleValidation() {
        Instant startAt = Instant.parse("2026-09-04T00:00:00Z");
        Instant endAt = Instant.parse("2026-09-04T01:00:00Z");

        assertThatThrownBy(() -> new RecurrenceSchedule(startAt, endAt, false, null))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_RECURRENCE_SCHEDULE));
    }
}
