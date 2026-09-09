package com.calio.calendar.notification.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterIosPushDeviceRequest(
        @NotBlank(message = "iOS 설치 식별자는 공백일 수 없습니다.") @Size(max = 128) String installationId,
        @NotBlank(message = "APNs 토큰은 공백일 수 없습니다.") @Size(max = 512) String apnsToken
) {
}
