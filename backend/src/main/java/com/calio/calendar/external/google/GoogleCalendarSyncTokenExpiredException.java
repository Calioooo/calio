package com.calio.calendar.external.google;

public class GoogleCalendarSyncTokenExpiredException extends RuntimeException {

    public GoogleCalendarSyncTokenExpiredException() {
        this(null);
    }

    public GoogleCalendarSyncTokenExpiredException(Throwable cause) {
        super("Google Calendar sync token expired.", cause);
    }
}
