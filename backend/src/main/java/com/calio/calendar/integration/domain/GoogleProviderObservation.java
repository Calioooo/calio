package com.calio.calendar.integration.domain;

import java.time.Instant;

public record GoogleProviderObservation(
        String etag,
        Instant updatedAt,
        String contentHash
) {

    public GoogleProviderObservation {
        contentHash = GoogleContentHash.requireValid(contentHash);
    }
}
