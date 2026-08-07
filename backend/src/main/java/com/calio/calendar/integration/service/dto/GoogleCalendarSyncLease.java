package com.calio.calendar.integration.service.dto;

public record GoogleCalendarSyncLease(
        Long integrationId,
        Long accountId,
        String nextSyncToken,
        String runId
) {
}
