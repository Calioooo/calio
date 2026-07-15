package com.calio.calendar.integration.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleCalendarConnectRequest(
        @NotBlank(message = "Google authorization code is required.") String authorizationCode
) {
}
