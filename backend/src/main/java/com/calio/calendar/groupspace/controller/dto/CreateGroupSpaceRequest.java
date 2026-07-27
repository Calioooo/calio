package com.calio.calendar.groupspace.controller.dto;

import jakarta.validation.constraints.NotNull;

public record CreateGroupSpaceRequest(
        @NotNull String name,
        String emoji,
        @NotNull String nickname
) {
}
