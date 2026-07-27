package com.calio.calendar.groupspace.controller.dto;

public record GroupOwnerDelegateDto(
        GroupMemberResponse previousOwner,
        GroupMemberResponse owner
) {
}
