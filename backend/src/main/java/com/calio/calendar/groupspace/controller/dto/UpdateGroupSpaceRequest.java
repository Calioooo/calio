package com.calio.calendar.groupspace.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateGroupSpaceRequest(
        @NotNull String name,
        String emoji
) {
}
