package com.calio.calendar.groupinvitation.controller.dto;

import java.time.Instant;

public record IssueGroupInvitationResponse(
        Long invitationId,
        String inviteUrl,
        String inviteCode,
        Instant expiresAt
) {

    @Override
    public String toString() {
        return "IssueGroupInvitationResponse[invitationId=%s, credentials=REDACTED, expiresAt=%s]"
                .formatted(invitationId, expiresAt);
    }
}
