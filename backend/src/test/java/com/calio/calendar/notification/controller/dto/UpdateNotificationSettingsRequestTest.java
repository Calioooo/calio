package com.calio.calendar.notification.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UpdateNotificationSettingsRequestTest {

    @Test
    @DisplayName("알림 정책에 정의된 lead time만 허용한다")
    void givenSupportedLeadTimes_whenValidate_thenReturnsTrue() {
        UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest(
                true,
                10,
                120,
                LocalTime.of(9, 0),
                false,
                LocalTime.of(8, 0)
        );

        assertThat(request.hasSupportedReminderMinutes()).isTrue();
    }

    @Test
    @DisplayName("정의되지 않은 lead time은 거절한다")
    void givenUnsupportedLeadTime_whenValidate_thenReturnsFalse() {
        UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest(
                true,
                7,
                120,
                LocalTime.of(9, 0),
                false,
                LocalTime.of(8, 0)
        );

        assertThat(request.hasSupportedReminderMinutes()).isFalse();
    }
}
