package com.calio.calendar.groupinvitation.controller.dto;

import com.calio.calendar.groupspace.domain.GroupSpace;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

public record PreviewGroupInvitationResponse(
        String name,
        @JsonInclude(JsonInclude.Include.ALWAYS) String emoji,
        int memberCount,
        Instant expiresAt
) {

    public static PreviewGroupInvitationResponse from(
            GroupSpace groupSpace,
            int memberCount,
            Instant expiresAt
    ) {
        return new PreviewGroupInvitationResponse(
                groupSpace.getName(),
                groupSpace.getEmoji(),
                memberCount,
                expiresAt
        );
    }
}
