package com.calio.calendar.integration.controller.dto;

import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarSyncMode;

public record GoogleCalendarSyncResponse(
        String calendarKey,
        GoogleCalendarSyncMode mode
) {

    public static GoogleCalendarSyncResponse from(GoogleCalendarSyncMode mode) {
        return new GoogleCalendarSyncResponse(
                GoogleCalendarEventMapping.PRIMARY_CALENDAR_KEY,
                mode
        );
    }
}
