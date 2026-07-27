package com.calio.calendar.groupspace.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupInvitationTest {

    @Test
    @DisplayName("invitation은 expiresAt 직전까지 유효하고 now가 expiresAt과 같으면 만료다")
    void expirationUsesExclusiveExpiresAtBoundary() {
        Instant expiresAt = Instant.parse("2026-07-28T10:00:00Z");
        GroupInvitation invitation = new GroupInvitation(
                null,
                null,
                new byte[32],
                new byte[32],
                expiresAt
        );

        assertThat(invitation.isExpired(expiresAt.minusNanos(1))).isFalse();
        assertThat(invitation.isExpired(expiresAt)).isTrue();
    }
}
