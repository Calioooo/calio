package com.calio.calendar.integration.mapping.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarContentHashTest {

    @Test
    @DisplayName("64자리 소문자 SHA-256 hex hash를 허용한다")
    void givenLowercaseSha256Hash_whenCreate_thenKeepsValue() {
        String hash = "a".repeat(64);

        assertThat(new GoogleCalendarContentHash(hash).value()).isEqualTo(hash);
    }

    @Test
    @DisplayName("SHA-256 형식이 아닌 hash를 거부한다")
    void givenInvalidHash_whenCreate_thenRejectsValue() {
        assertThatThrownBy(() -> new GoogleCalendarContentHash("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
