package com.calio.calendar.integration.sync.page.dto;

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
