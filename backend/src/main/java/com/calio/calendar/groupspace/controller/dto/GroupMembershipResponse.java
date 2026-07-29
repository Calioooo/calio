package com.calio.calendar.groupspace.controller.dto;

import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberRole;
import com.calio.calendar.groupspace.domain.GroupSpace;
import java.time.Instant;

public record GroupMembershipResponse(
        String nickname,
        GroupMemberRole role,
        Instant createdAt,
        Instant updatedAt,
        Instant statusChangedAt
) {

    public static GroupMembershipResponse from(GroupMember member, GroupSpace groupSpace) {
        return new GroupMembershipResponse(
                member.getNickname(),
                member.roleIn(groupSpace),
                member.getCreatedAt(),
                member.getStatusChangedAt(),
                member.getStatusChangedAt()
        );
    }
}
