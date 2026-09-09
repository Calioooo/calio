package com.calio.calendar.notification.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("iOS 푸시 기기 등록 요청의 공백 및 필수 필드는 명시적인 validation 메시지를 반환한다")
    void registerIosPushDeviceRequestUsesExplicitRequiredFieldMessages() {
        RegisterIosPushDeviceRequest request = new RegisterIosPushDeviceRequest(null, " ");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder(
                        "iOS 설치 식별자는 공백일 수 없습니다.",
                        "APNs 토큰은 공백일 수 없습니다."
                );
    }
}
