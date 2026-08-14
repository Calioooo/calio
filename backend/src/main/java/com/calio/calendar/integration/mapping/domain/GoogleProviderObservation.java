package com.calio.calendar.integration.mapping.domain;

import java.time.Instant;
import java.util.Objects;

public record GoogleProviderObservation(
        String etag,
        Instant updatedAt,
        String contentHash
) {

    public GoogleProviderObservation {
        if (contentHash == null || !contentHash.matches("^v1:[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("Google provider content hash must be a v1 SHA-256 digest");
        }
    }
}
