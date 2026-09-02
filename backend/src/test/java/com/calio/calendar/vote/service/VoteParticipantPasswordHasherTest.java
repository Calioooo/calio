package com.calio.calendar.vote.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VoteParticipantPasswordHasherTest {

    private final VoteParticipantPasswordHasher passwordHasher = new VoteParticipantPasswordHasher();

    @Test
    @DisplayName("참여자 비밀번호는 BCrypt로 해시하고 원문과 비교해 검증한다")
    void hashStoresBcryptValueThatMatchesOnlyTheOriginalPassword() {
        // when
        String passwordHash = passwordHasher.hash("participant-password");

        // then
        assertThat(passwordHash).isNotEqualTo("participant-password");
        assertThat(passwordHash).startsWith("$2");
        assertThat(passwordHasher.matches("participant-password", passwordHash)).isTrue();
        assertThat(passwordHasher.matches("wrong-password", passwordHash)).isFalse();
    }
}
