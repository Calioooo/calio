package com.calio.calendar.external.google;

public class GoogleCalendarSyncTokenExpiredException extends RuntimeException {

    public GoogleCalendarSyncTokenExpiredException() {
        super("Google Calendar sync token expired.");
    }
}
