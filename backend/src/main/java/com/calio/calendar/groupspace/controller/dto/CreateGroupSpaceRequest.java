package com.calio.calendar.groupspace.controller.dto;

public record CreateGroupSpaceRequest(
        String name,
        String emoji,
        String nickname
) {
}
