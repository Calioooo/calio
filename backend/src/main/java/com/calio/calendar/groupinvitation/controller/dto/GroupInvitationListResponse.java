package com.calio.calendar.groupinvitation.controller.dto;

import java.util.List;

public record GroupInvitationListResponse(
        List<GroupInvitationSummaryResponse> invitations
) {

    public GroupInvitationListResponse {
        invitations = List.copyOf(invitations);
    }
}
