package com.calio.calendar.groupinvitation.controller.dto;

import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import java.time.Instant;

public record IssueGroupInvitationResponse(
        Long invitationId,
        String inviteUrl,
        String inviteCode,
        Instant expiresAt
) {

    public static IssueGroupInvitationResponse from(
            GroupInvitation invitation,
            String inviteUrl,
            String inviteCode
    ) {
        return new IssueGroupInvitationResponse(
                invitation.getId(),
                inviteUrl,
                inviteCode,
                invitation.getExpiresAt()
        );
    }
}
