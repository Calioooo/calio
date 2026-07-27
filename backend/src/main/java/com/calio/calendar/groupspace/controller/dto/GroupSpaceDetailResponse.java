package com.calio.calendar.groupspace.controller.dto;

import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

public record GroupSpaceDetailResponse(
        Long groupSpaceId,
        String name,
        @JsonInclude(JsonInclude.Include.ALWAYS) String emoji,
        int memberCount,
        GroupMembershipResponse myMembership,
        Instant createdAt,
        Instant updatedAt
) {

    public static GroupSpaceDetailResponse from(
            GroupSpace groupSpace,
            GroupMember membership,
            int memberCount
    ) {
        return new GroupSpaceDetailResponse(
                groupSpace.getId(),
                groupSpace.getName(),
                groupSpace.getEmoji(),
                memberCount,
                GroupMembershipResponse.from(membership, groupSpace),
                groupSpace.getCreatedAt(),
                groupSpace.getUpdatedAt()
        );
    }
}
