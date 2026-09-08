package com.calio.calendar.integration.sync.operation.domain;

public enum GoogleCalendarRecurrenceJobKind {
    MASTER_CREATE,
    MASTER_UPDATE,
    MASTER_DELETE,
    OVERRIDE_UPSERT,
    OVERRIDE_DELETE
}
