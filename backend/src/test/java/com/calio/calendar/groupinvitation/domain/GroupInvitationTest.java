package com.calio.calendar.groupinvitation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupInvitationTest {

    @Test
    @DisplayName("now가 expiresAt과 같아지는 순간 invitation은 즉시 만료된다")
    void expiresAtBoundaryIsExclusive() {
        // given
        Instant expiresAt = Instant.parse("2026-07-29T08:00:00Z");
        GroupInvitation invitation = new GroupInvitation(
                1L,
                2L,
                new byte[32],
                new byte[32],
                expiresAt
        );

        // when, then
        assertThat(invitation.isExpiredAt(expiresAt.minusNanos(1))).isFalse();
        assertThat(invitation.isExpiredAt(expiresAt)).isTrue();
        assertThat(invitation.isExpiredAt(expiresAt.plusNanos(1))).isTrue();
    }
}
