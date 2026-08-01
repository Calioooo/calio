package com.calio.calendar.groupinvitation.service;

record InvitationCredentialPair(
        String linkToken,
        String inviteCode,
        byte[] linkTokenHash,
        byte[] inviteCodeHash
) {

    @Override
    public String toString() {
        return "InvitationCredentialPair[REDACTED]";
    }
}
