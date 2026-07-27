package com.calio.calendar.groupspace.controller.dto;

import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberRole;

public record GroupMemberResponse(
        Long memberId,
        String nickname,
        GroupMemberRole role
) {
    public static GroupMemberResponse from(GroupMember member, Long ownerAccountId) {
        GroupMemberRole role = ownerAccountId.equals(member.getAccountId())
                ? GroupMemberRole.OWNER
                : GroupMemberRole.MEMBER;
        return new GroupMemberResponse(member.getId(), member.getNickname(), role);
    }
}
