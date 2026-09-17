package com.calio.calendar.notification.client;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record ApnsMessage(
    String token, String title, String body, Map<String, String> metadata, Instant expiration) {

  public ApnsMessage {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("APNs token is required.");
    }
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("APNs alert title is required.");
    }
    if (body == null || body.isBlank()) {
      throw new IllegalArgumentException("APNs alert body is required.");
    }
    metadata = Map.copyOf(Objects.requireNonNull(metadata, "APNs metadata is required."));
    Objects.requireNonNull(expiration, "APNs expiration is required.");
  }
}
