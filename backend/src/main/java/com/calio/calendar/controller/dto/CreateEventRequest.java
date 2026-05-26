package com.calio.calendar.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CreateEventRequest(
        @NotBlank(message = "title must not be blank")
        String title,
        String description,
        @NotNull(message = "startAt is required")
        Instant startAt,
        @NotNull(message = "endAt is required")
        Instant endAt
) {
}
