package com.calio.calendar.sharing.controller.dto;

public record GroupShareTargetResponse(Long groupSpaceId, GroupShareTargetStatus status) {

    public static GroupShareTargetResponse from(Long groupSpaceId, GroupShareTargetStatus status) {
        return new GroupShareTargetResponse(groupSpaceId, status);
    }
}
