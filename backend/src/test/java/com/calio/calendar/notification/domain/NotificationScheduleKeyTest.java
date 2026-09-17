package com.calio.calendar.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationScheduleKeyTest {

  @Test
  @DisplayName("일반 일정과 반복 회차는 서로 다른 안정적인 알림 식별자를 만든다")
  void givenScheduleIdentity_whenCreateKey_thenKeepsScheduleKindAndIdentity() {
    // given
    Instant originStartAt = Instant.parse("2026-09-08T00:00:00Z");

    // when & then
    assertThat(NotificationScheduleKey.personalEvent(1L).value()).isEqualTo("personal:1");
    assertThat(NotificationScheduleKey.personalRecurrence(2L, originStartAt).value())
        .isEqualTo("personal-recurrence:2:2026-09-08T00:00:00Z");
    assertThat(NotificationScheduleKey.groupEvent(3L).value()).isEqualTo("group:3");
    assertThat(NotificationScheduleKey.groupRecurrence(4L, originStartAt).value())
        .isEqualTo("group-recurrence:4:2026-09-08T00:00:00Z");
    assertThat(NotificationScheduleKey.briefing(LocalDate.of(2026, 9, 8)).value())
        .isEqualTo("briefing:2026-09-08");
  }
}
