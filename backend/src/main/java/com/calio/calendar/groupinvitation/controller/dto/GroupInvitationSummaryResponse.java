package com.calio.calendar.groupinvitation.controller.dto;

import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import java.time.Instant;

public record GroupInvitationSummaryResponse(
        Long invitationId,
        Instant expiresAt
) {

    public static GroupInvitationSummaryResponse from(GroupInvitation invitation) {
        return new GroupInvitationSummaryResponse(
                invitation.getId(),
                invitation.getExpiresAt()
        );
    }
}
