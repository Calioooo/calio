package com.calio.calendar.groupspace.controller.dto;

public record TransferGroupOwnerResponse(
        GroupMemberProjection previousOwner,
        GroupMemberProjection owner
) {
}
