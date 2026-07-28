package com.calio.calendar.groupinvitation.controller.dto;

import com.calio.calendar.groupinvitation.repository.InvitationSummaryProjection;
import java.time.Instant;

public record GroupInvitationSummaryResponse(
        Long invitationId,
        Instant expiresAt
) {

    public static GroupInvitationSummaryResponse from(InvitationSummaryProjection invitation) {
        return new GroupInvitationSummaryResponse(
                invitation.getInvitationId(),
                invitation.getExpiresAt()
        );
    }
}
