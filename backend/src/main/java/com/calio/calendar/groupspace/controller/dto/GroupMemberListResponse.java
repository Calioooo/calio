package com.calio.calendar.groupspace.controller.dto;

import java.util.List;

public record GroupMemberListResponse(List<GroupMemberResponse> members) {
    public GroupMemberListResponse {
        members = List.copyOf(members);
    }
}
