package com.calio.calendar.notification.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("알림 설정 요청의 필수 필드는 명시적인 validation 메시지를 반환한다")
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
    @DisplayName("iOS 푸시 기기 등록 요청의 필수 필드는 명시적인 validation 메시지를 반환한다")
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
