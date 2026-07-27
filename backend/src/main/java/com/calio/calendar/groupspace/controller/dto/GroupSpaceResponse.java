package com.calio.calendar.groupspace.controller.dto;

import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import java.time.Instant;

public record GroupSpaceResponse(
        Long id,
        String name,
        String emoji,
        GroupMembershipResponse myMembership,
        int memberCount,
        Instant createdAt
) {
    public static GroupSpaceResponse from(GroupSpace groupSpace, GroupMember membership, int memberCount) {
        return new GroupSpaceResponse(
                groupSpace.getId(),
                groupSpace.getName(),
                groupSpace.getEmoji(),
                GroupMembershipResponse.from(membership, groupSpace.getOwnerAccountId()),
                memberCount,
                groupSpace.getCreatedAt()
        );
    }
}
