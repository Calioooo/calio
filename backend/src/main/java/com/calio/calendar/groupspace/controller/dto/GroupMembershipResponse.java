package com.calio.calendar.groupspace.controller.dto;

import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberRole;

public record GroupMembershipResponse(
        Long memberId,
        String nickname,
        GroupMemberRole role
) {

    public static GroupMembershipResponse from(GroupMember member, GroupMemberRole role) {
        return new GroupMembershipResponse(member.getId(), member.getNickname(), role);
    }
}
