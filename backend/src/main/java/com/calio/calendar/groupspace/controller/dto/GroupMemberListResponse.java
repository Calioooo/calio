package com.calio.calendar.groupspace.controller.dto;

import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import java.util.List;

public record GroupMemberListResponse(List<GroupMemberProjection> members) {

    public static GroupMemberListResponse from(List<GroupMember> members, GroupSpace groupSpace) {
        return new GroupMemberListResponse(
                members.stream()
                        .map(member -> GroupMemberProjection.from(member, groupSpace))
                        .toList()
        );
    }
}
