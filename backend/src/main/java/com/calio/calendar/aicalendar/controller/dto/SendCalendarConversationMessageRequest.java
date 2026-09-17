package com.calio.calendar.aicalendar.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record SendCalendarConversationMessageRequest(
        @NotBlank String message,
        @NotBlank String timeZone
) {
}
