package com.calio.calendar.integration.domain;

import java.time.Instant;

public record GoogleCalendarItemSnapshot(
        String etag,
        Instant updatedAt,
        String contentHash
) {

    public GoogleCalendarItemSnapshot {
        contentHash = GoogleContentHash.requireValid(contentHash);
    }
}
