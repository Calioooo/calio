package com.calio.calendar.integration.sync.operation.domain;

public enum GoogleCalendarEffectiveScopeType {

    EVENT("GENERAL_EVENT"),
    RECURRENCE_EVENT("RECURRENCE_MASTER"),
    RECURRENCE_OVERRIDE("RECURRENCE_OVERRIDE");

    private final String storedValue;

    GoogleCalendarEffectiveScopeType(String storedValue) {
        this.storedValue = storedValue;
    }

    public String getStoredValue() {
        return storedValue;
    }

}
