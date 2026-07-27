package com.calio.calendar.groupspace.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateGroupSpaceRequest(
        @NotNull String name,
        String emoji,
        @NotNull String nickname
) {
}
