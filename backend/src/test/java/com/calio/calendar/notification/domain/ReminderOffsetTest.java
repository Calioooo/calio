package com.calio.calendar.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReminderOffsetTest {

    @Test
    @DisplayName("시간 일정 offset은 시작 시각과 지원되는 분 단위를 구분한다")
    void timedReminderOffsetHasItsOwnSupportedValues() {
        assertThat(TimedReminderOffset.AT_START.minutes()).isZero();
        assertThat(TimedReminderOffset.MINUTES_10.minutes()).isEqualTo(10);
        assertThat(TimedReminderOffset.NONE.isDisabled()).isTrue();
    }

    @Test
    @DisplayName("중요 일정 offset은 추가 알림에 허용된 분 단위만 가진다")
    void importantReminderOffsetHasItsOwnSupportedValues() {
        assertThat(ImportantReminderOffset.MINUTES_30.minutes()).isEqualTo(30);
        assertThat(ImportantReminderOffset.MINUTES_120.minutes()).isEqualTo(120);
        assertThat(ImportantReminderOffset.NONE.isDisabled()).isTrue();
    }
}
