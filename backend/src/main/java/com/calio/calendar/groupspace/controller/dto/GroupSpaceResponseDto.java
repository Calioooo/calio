package com.calio.calendar.groupspace.controller.dto;

import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberRole;
import com.calio.calendar.groupspace.domain.GroupSpace;
import java.time.Instant;

public record GroupSpaceResponseDto(
        Long id,
        String name,
        String emoji,
        GroupMembershipResponse myMembership,
        int memberCount,
        Instant createdAt,
        Instant updatedAt
) {

    public static GroupSpaceResponseDto from(
            GroupSpace groupSpace,
            GroupMember member,
            int memberCount
    ) {
        GroupMemberRole role = groupSpace.isOwner(member.getAccountId())
                ? GroupMemberRole.OWNER
                : GroupMemberRole.MEMBER;
        return new GroupSpaceResponseDto(
                groupSpace.getId(),
                groupSpace.getName(),
                groupSpace.getEmoji(),
                GroupMembershipResponse.from(member, role),
                memberCount,
                groupSpace.getCreatedAt(),
                groupSpace.getUpdatedAt()
        );
    }
}
