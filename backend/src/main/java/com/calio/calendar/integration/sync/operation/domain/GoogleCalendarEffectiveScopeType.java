package com.calio.calendar.integration.sync.operation.domain;

import java.util.Arrays;

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

    public static GoogleCalendarEffectiveScopeType fromStoredValue(String storedValue) {
        return Arrays.stream(values())
                .filter(type -> type.storedValue.equals(storedValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported Google Calendar effective scope: " + storedValue));
    }
}
