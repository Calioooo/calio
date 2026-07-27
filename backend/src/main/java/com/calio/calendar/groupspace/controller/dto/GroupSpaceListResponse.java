package com.calio.calendar.groupspace.controller.dto;

import java.util.List;

public record GroupSpaceListResponse(
        List<GroupSpaceSummaryResponse> groupSpaces
) {

    public GroupSpaceListResponse {
        groupSpaces = List.copyOf(groupSpaces);
    }
}
