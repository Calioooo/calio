package com.calio.calendar.groupspace.controller.dto;

import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;

public record TransferGroupOwnerResponse(
        GroupMemberProjection previousOwner,
        GroupMemberProjection owner
) {

    public static TransferGroupOwnerResponse from(
            GroupSpace groupSpace,
            GroupMember previousOwner,
            GroupMember owner
    ) {
        return new TransferGroupOwnerResponse(
                GroupMemberProjection.from(previousOwner, groupSpace),
                GroupMemberProjection.from(owner, groupSpace)
        );
    }
}
