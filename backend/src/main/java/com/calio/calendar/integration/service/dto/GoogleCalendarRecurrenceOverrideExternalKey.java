package com.calio.calendar.integration.service.dto;

public record GoogleCalendarRecurrenceOverrideExternalKey(
        String recurrenceEventExternalId,
        String overrideExternalEventId
) {
}
