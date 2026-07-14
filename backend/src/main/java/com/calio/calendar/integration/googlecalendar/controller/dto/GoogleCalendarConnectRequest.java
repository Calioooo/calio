package com.calio.calendar.integration.googlecalendar.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleCalendarConnectRequest(
        @NotBlank(message = "Google authorizationCode는 공백일 수 없습니다.") String authorizationCode
) {
}
