package com.calio.calendar.external.google;

import com.calio.calendar.external.google.dto.GoogleCalendarEventItem;

public sealed interface GoogleCalendarEventLookupResult
        permits GoogleCalendarEventLookupResult.Found, GoogleCalendarEventLookupResult.NotFound {

    record Found(GoogleCalendarEventItem event) implements GoogleCalendarEventLookupResult {

        public Found {
            if (event == null) {
                throw new IllegalArgumentException("event must not be null");
            }
        }
    }

    record NotFound() implements GoogleCalendarEventLookupResult {
    }
}
