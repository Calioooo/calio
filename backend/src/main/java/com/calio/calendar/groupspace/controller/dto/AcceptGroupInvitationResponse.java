package com.calio.calendar.groupspace.controller.dto;

import com.calio.calendar.groupspace.domain.GroupJoinResult;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;

public record AcceptGroupInvitationResponse(
        GroupJoinResult joinResult,
        GroupSpaceResponse groupSpace,
        GroupMembershipResponse membership
) {
    public static AcceptGroupInvitationResponse from(
            GroupJoinResult joinResult,
            GroupSpace groupSpace,
            GroupMember membership,
            int memberCount
    ) {
        GroupMembershipResponse membershipResponse =
                GroupMembershipResponse.from(membership, groupSpace.getOwnerAccountId());
        return new AcceptGroupInvitationResponse(
                joinResult,
                GroupSpaceResponse.from(groupSpace, membership, memberCount),
                membershipResponse
        );
    }
}
