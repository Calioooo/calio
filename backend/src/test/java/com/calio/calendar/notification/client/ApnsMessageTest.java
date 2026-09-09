package com.calio.calendar.notification.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApnsMessageTest {

    @Test
    @DisplayName("APNs message는 token, payload, expiration이 모두 필요하다")
    void givenMissingRequiredValue_whenCreate_thenRejectsMessage() {
        assertThatThrownBy(() -> new ApnsMessage(null, "{}", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApnsMessage(" ", "{}", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApnsMessage("token", null, Instant.now()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ApnsMessage("token", "{}", null))
                .isInstanceOf(NullPointerException.class);
    }
}
