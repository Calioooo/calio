package com.calio.calendar.groupspace.controller.dto;

import com.calio.calendar.groupspace.domain.GroupMember;

public record GroupInvitationIssuerResponse(Long memberId, String nickname) {
    public static GroupInvitationIssuerResponse from(GroupMember issuer) {
        return new GroupInvitationIssuerResponse(issuer.getId(), issuer.getNickname());
    }
}
