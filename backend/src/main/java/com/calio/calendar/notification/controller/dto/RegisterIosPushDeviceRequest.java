package com.calio.calendar.notification.controller.dto;

import com.calio.calendar.notification.domain.IosNotificationAuthorizationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterIosPushDeviceRequest(
        @NotBlank(message = "iOS 설치 식별자는 공백일 수 없습니다.") @Size(max = 128) String installationId,
        @NotBlank(message = "APNs 토큰은 공백일 수 없습니다.") @Size(max = 512) String apnsToken,
        @NotNull(message = "iOS 알림 권한 상태는 필수입니다.") IosNotificationAuthorizationStatus authorizationStatus
) {
}
