package com.calio.calendar.groupspace.controller.dto;

import java.time.Instant;

public record CreateGroupInvitationResponse(
        Long invitationId,
        String inviteUrl,
        String inviteCode,
        Instant expiresAt
) {
}
