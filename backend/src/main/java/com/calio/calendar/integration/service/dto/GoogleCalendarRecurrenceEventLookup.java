package com.calio.calendar.integration.service.dto;

public sealed interface GoogleCalendarRecurrenceEventLookup
        permits GoogleCalendarRecurrenceEventLookup.FoundRecurrenceEvent,
        GoogleCalendarRecurrenceEventLookup.MissingRecurrenceEvent {

    record FoundRecurrenceEvent(
            GoogleCalendarNormalizedPage.RecurrenceEventUpsert recurrenceEvent
    )
            implements GoogleCalendarRecurrenceEventLookup {
    }

    enum MissingRecurrenceEvent implements GoogleCalendarRecurrenceEventLookup {
        INSTANCE
    }
}
