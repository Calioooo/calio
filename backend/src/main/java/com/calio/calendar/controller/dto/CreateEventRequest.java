package com.calio.calendar.controller.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateEventRequest(
        @NotBlank String title,
        String description,
        @NotNull Instant startAt,
        @NotNull Instant endAt
) {
}
