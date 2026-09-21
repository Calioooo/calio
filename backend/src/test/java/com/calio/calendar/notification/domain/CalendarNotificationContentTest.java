package com.calio.calendar.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CalendarNotificationContentTest {

  @Test
  @DisplayName("개인 일정 알림에는 일정 제목만 노출한다")
  void givenPersonalSchedule_whenCreateContent_thenUsesTitleOnly() {
    // when
    CalendarNotificationContent content =
        CalendarNotificationContent.schedule(
            CalendarNotificationType.REMINDER, LocalDate.of(2026, 9, 8), "회의", null);

    // then
    assertThat(content.body()).isEqualTo("회의");
  }

  @Test
  @DisplayName("그룹 일정 알림에는 일정 제목과 그룹명만 노출한다")
  void givenGroupSchedule_whenCreateContent_thenUsesTitleAndGroupName() {
    // when
    CalendarNotificationContent content =
        CalendarNotificationContent.schedule(
            CalendarNotificationType.REMINDER, LocalDate.of(2026, 9, 8), "회의", "제품팀");

    // then
    assertThat(content.body()).isEqualTo("회의 · 제품팀");
  }

  @Test
  @DisplayName("브리핑은 일정 제목 대신 당일 전체 일정 수를 노출한다")
  void givenScheduleCount_whenCreateBriefing_thenUsesTotalCount() {
    // when
    CalendarNotificationContent content =
        CalendarNotificationContent.briefing(LocalDate.of(2026, 9, 8), 8);

    // then
    assertThat(content.body()).isEqualTo("오늘 일정이 8개 있어요");
  }
}
