package com.calio.calendar.groupspace.controller.dto;

import com.calio.calendar.groupspace.domain.GroupJoinResult;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;

public record AcceptGroupInvitationResponse(
        GroupJoinResult joinResult,
        GroupSpaceJoinResponse groupSpace,
        GroupMemberProjection membership
) {

    public static AcceptGroupInvitationResponse from(
            GroupJoinResult joinResult,
            GroupSpace groupSpace,
            GroupMember membership,
            int memberCount
    ) {
        GroupMemberProjection projection = GroupMemberProjection.from(membership, groupSpace);
        return new AcceptGroupInvitationResponse(
                joinResult,
                GroupSpaceJoinResponse.from(groupSpace, membership, memberCount),
                projection
        );
    }
}
