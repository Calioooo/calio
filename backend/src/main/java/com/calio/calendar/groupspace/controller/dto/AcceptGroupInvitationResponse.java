package com.calio.calendar.groupspace.controller.dto;

import com.calio.calendar.groupspace.domain.GroupJoinResult;

public record AcceptGroupInvitationResponse(
        GroupJoinResult joinResult,
        GroupSpaceJoinResponse groupSpace,
        GroupMemberProjection membership
) {
}
