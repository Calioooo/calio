package com.calio.calendar.notification.controller.dto;

import com.calio.calendar.notification.domain.IosNotificationAuthorizationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterIosNotificationEndpointRequest(
        @NotBlank @Size(max = 128) String installationId,
        @NotBlank @Size(max = 512) String apnsToken,
        @NotNull IosNotificationAuthorizationStatus authorizationStatus
) {
}
