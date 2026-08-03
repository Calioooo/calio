package com.calio.calendar.integration.service;

public record GoogleCalendarOperationResult(
        String nextSyncToken,
        boolean conflictDetected
) {
    public static GoogleCalendarOperationResult success() {
        return new GoogleCalendarOperationResult(null, false);
    }
}
