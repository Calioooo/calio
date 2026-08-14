package com.calio.calendar.groupinvitation.service.dto;

public record InvitationCredentialPair(
        String linkToken,
        String inviteCode,
        byte[] linkTokenHash,
        byte[] inviteCodeHash
) {
}
