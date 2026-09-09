package com.calio.calendar.notification.domain;

public enum TimedReminderOffset {
    NONE,
    AT_START,
    MINUTES_5,
    MINUTES_10,
    MINUTES_30,
    MINUTES_60,
    MINUTES_120,
    MINUTES_1440;

    public boolean isDisabled() {
        return this == NONE;
    }

    public int minutes() {
        return switch (this) {
            case AT_START -> 0;
            case MINUTES_5 -> 5;
            case MINUTES_10 -> 10;
            case MINUTES_30 -> 30;
            case MINUTES_60 -> 60;
            case MINUTES_120 -> 120;
            case MINUTES_1440 -> 1440;
            case NONE -> throw new IllegalStateException("Disabled reminder does not have an offset.");
        };
    }
}
