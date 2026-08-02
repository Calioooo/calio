package com.calio.calendar.groupspace.controller.dto;

import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

public record GroupSpaceSummaryResponse(
        Long groupSpaceId,
        String name,
        @JsonInclude(JsonInclude.Include.ALWAYS) String emoji,
        int memberCount,
        GroupMembershipResponse myMembership,
        Instant createdAt,
        Instant updatedAt
) {

    public static GroupSpaceSummaryResponse from(
            GroupSpace groupSpace,
            GroupMember membership,
            int memberCount
    ) {
        return new GroupSpaceSummaryResponse(
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
