package com.calio.calendar.account.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReminderOffsetTest {

  @Test
  @DisplayName("시간 일정 알림 offset은 시작 시각과 비활성화를 구분한다")
  void timedReminderOffsetKeepsStartAndDisabledMeaning() {
    assertThat(TimedReminderOffset.AT_START.minutes()).isZero();
    assertThat(TimedReminderOffset.MINUTES_10.minutes()).isEqualTo(10);
    assertThat(TimedReminderOffset.NONE.isDisabled()).isTrue();
  }

  @Test
  @DisplayName("중요 일정 알림 offset은 일반 일정과 독립된 선택지를 제공한다")
  void importantReminderOffsetKeepsIndependentOptions() {
    assertThat(ImportantReminderOffset.MINUTES_30.minutes()).isEqualTo(30);
    assertThat(ImportantReminderOffset.MINUTES_120.minutes()).isEqualTo(120);
    assertThat(ImportantReminderOffset.NONE.isDisabled()).isTrue();
  }
}
