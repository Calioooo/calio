package com.calio.calendar.integration.sync.operation.domain;

public enum GoogleCalendarRecurrenceJobKind {
    RECURRENCE_CREATE,
    RECURRENCE_UPDATE,
    RECURRENCE_DELETE,
    OVERRIDE_UPSERT,
    OVERRIDE_DELETE
}
