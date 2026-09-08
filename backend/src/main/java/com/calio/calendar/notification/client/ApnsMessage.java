package com.calio.calendar.notification.client;

import java.time.Instant;
import java.util.Objects;

public record ApnsMessage(String token, String payload, Instant expiration) {

    public ApnsMessage {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("APNs token is required.");
        }
        Objects.requireNonNull(payload, "APNs payload is required.");
        Objects.requireNonNull(expiration, "APNs expiration is required.");
    }
}
