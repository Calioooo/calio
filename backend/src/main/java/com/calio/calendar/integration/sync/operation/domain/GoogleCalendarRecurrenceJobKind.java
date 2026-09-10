package com.calio.calendar.integration.sync.operation.domain;

public enum GoogleCalendarRecurrenceJobKind {
    RECURRENCE_CREATE,
    RECURRENCE_UPDATE,
    RECURRENCE_DELETE,
    OVERRIDE_UPSERT,
    OVERRIDE_DELETE;

    public boolean isRecurrenceJob() {
        return this == RECURRENCE_CREATE || this == RECURRENCE_UPDATE || this == RECURRENCE_DELETE;
    }

    public boolean isOverrideJob() {
        return this == OVERRIDE_UPSERT || this == OVERRIDE_DELETE;
    }
}
