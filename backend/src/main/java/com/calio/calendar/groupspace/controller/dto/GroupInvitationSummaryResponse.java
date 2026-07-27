package com.calio.calendar.groupspace.controller.dto;

import com.calio.calendar.groupspace.domain.GroupInvitation;
import com.calio.calendar.groupspace.domain.GroupInvitationStatus;
import java.time.Instant;

public record GroupInvitationSummaryResponse(
        Long invitationId,
        GroupInvitationIssuerResponse issuer,
        Instant expiresAt,
        GroupInvitationStatus status
) {
    public static GroupInvitationSummaryResponse from(GroupInvitation invitation, Instant now) {
        return new GroupInvitationSummaryResponse(
                invitation.getId(),
                GroupInvitationIssuerResponse.from(invitation.getIssuer()),
                invitation.getExpiresAt(),
                GroupInvitationStatus.from(now, invitation.getExpiresAt())
        );
    }
}
