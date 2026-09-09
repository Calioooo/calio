package com.calio.calendar.notification.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class NotificationRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void updateNotificationSettingsRequestUsesExplicitRequiredFieldMessages() {
        UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest(
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder(
                        "일정 알림 사용 여부는 필수입니다.",
                        "시간 일정 알림 시각은 필수입니다.",
                        "중요 일정 추가 알림 시각은 필수입니다.",
                        "종일 일정 알림 시각은 필수입니다.",
                        "일일 브리핑 사용 여부는 필수입니다.",
                        "일일 브리핑 시각은 필수입니다."
                );
    }

    @Test
    void registerIosPushDeviceRequestUsesExplicitRequiredFieldMessages() {
        RegisterIosPushDeviceRequest request = new RegisterIosPushDeviceRequest(null, " ", null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder(
                        "iOS 설치 식별자는 공백일 수 없습니다.",
                        "APNs 토큰은 공백일 수 없습니다.",
                        "iOS 알림 권한 상태는 필수입니다."
                );
    }
}
