package com.calio.calendar.integration.sync.page.dto;

public record GoogleCalendarRecurrenceOverrideExternalKey(
        String recurrenceEventExternalId,
        String overrideExternalEventId
) {
}
