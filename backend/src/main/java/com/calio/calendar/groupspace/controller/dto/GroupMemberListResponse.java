package com.calio.calendar.groupspace.controller.dto;

import java.util.List;

public record GroupMemberListResponse(List<GroupMemberProjection> members) {
}
