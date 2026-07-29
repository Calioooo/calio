package com.calio.calendar.groupspace.controller.dto;

import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

public record GroupSpaceJoinResponse(
        Long id,
        String name,
        @JsonInclude(JsonInclude.Include.ALWAYS) String emoji,
        GroupMemberProjection myMembership,
        int memberCount,
        Instant createdAt
) {

    public static GroupSpaceJoinResponse from(
            GroupSpace groupSpace,
            GroupMember membership,
            int memberCount
    ) {
        return new GroupSpaceJoinResponse(
                groupSpace.getId(),
                groupSpace.getName(),
                groupSpace.getEmoji(),
                GroupMemberProjection.from(membership, groupSpace),
                memberCount,
                groupSpace.getCreatedAt()
        );
    }
}
