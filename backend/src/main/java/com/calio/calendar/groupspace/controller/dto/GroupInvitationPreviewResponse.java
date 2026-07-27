package com.calio.calendar.groupspace.controller.dto;

import com.calio.calendar.groupspace.domain.GroupInvitation;
import java.time.Instant;

public record GroupInvitationPreviewResponse(
        String name,
        String emoji,
        int memberCount,
        Instant expiresAt
) {
    public static GroupInvitationPreviewResponse from(GroupInvitation invitation, int memberCount) {
        return new GroupInvitationPreviewResponse(
                invitation.getGroupSpace().getName(),
                invitation.getGroupSpace().getEmoji(),
                memberCount,
                invitation.getExpiresAt()
        );
    }
}
