package com.calio.calendar.groupspace.controller.dto;

import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberRole;
import com.calio.calendar.groupspace.domain.GroupSpace;

public record GroupMemberProjection(Long memberId, String nickname, GroupMemberRole role) {

    public static GroupMemberProjection from(GroupMember member, GroupSpace groupSpace) {
        return new GroupMemberProjection(member.getId(), member.getNickname(), member.roleIn(groupSpace));
    }
}
