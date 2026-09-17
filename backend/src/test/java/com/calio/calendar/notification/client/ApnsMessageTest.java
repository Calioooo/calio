package com.calio.calendar.notification.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApnsMessageTest {

  @Test
  @DisplayName("APNs message는 token, alert와 expiration이 모두 필요하다")
  void givenMissingRequiredValue_whenCreate_thenRejectsMessage() {
    assertThatThrownBy(() -> new ApnsMessage(null, "Calio", "회의", Map.of(), Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ApnsMessage(" ", "Calio", "회의", Map.of(), Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ApnsMessage("token", null, "회의", Map.of(), Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ApnsMessage("token", "Calio", null, Map.of(), Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ApnsMessage("token", "Calio", "회의", null, Instant.now()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ApnsMessage("token", "Calio", "회의", Map.of(), null))
        .isInstanceOf(NullPointerException.class);
  }
}
