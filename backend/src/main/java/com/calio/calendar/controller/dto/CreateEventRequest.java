package com.calio.calendar.controller.dto;

import com.calio.calendar.repository.entity.Event;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateEventRequest(
        @NotBlank String title,
        String description,
        @NotNull OffsetDateTime startAt,
        @NotNull OffsetDateTime endAt
) {

    public Event toEntity() {
        return new Event(title, description, startAt, endAt);
    }
}
