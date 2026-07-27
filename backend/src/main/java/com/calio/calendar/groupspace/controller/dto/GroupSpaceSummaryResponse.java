package com.calio.calendar.groupspace.controller.dto;

import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;

public record GroupSpaceSummaryResponse(
        Long id,
        String name,
        String emoji,
        GroupMembershipResponse myMembership,
        int memberCount
) {
    public static GroupSpaceSummaryResponse from(GroupSpace groupSpace, GroupMember membership, int memberCount) {
        return new GroupSpaceSummaryResponse(
                groupSpace.getId(),
                groupSpace.getName(),
                groupSpace.getEmoji(),
                GroupMembershipResponse.from(membership, groupSpace.getOwnerAccountId()),
                memberCount
        );
    }
}
