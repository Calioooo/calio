package com.calio.calendar.groupspace.domain;

import java.time.Instant;

public enum GroupInvitationStatus {
    ACTIVE,
    EXPIRED;

    public static GroupInvitationStatus from(Instant now, Instant expiresAt) {
        return now.isBefore(expiresAt) ? ACTIVE : EXPIRED;
    }
}
