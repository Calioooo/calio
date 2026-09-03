package com.calio.calendar.integration.connection.controller.dto;

import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import java.time.Instant;

public record GoogleCalendarConnectionResponse(
        boolean connected,
        String googleEmail,
        String googleSubject,
        Instant connectedAt
) {

    public static GoogleCalendarConnectionResponse connected(GoogleCalendarConnection connection) {
        return new GoogleCalendarConnectionResponse(
                true,
                connection.getGoogleEmail(),
                connection.getGoogleSubject(),
                connection.getConnectedAt()
        );
    }

    public static GoogleCalendarConnectionResponse disconnected() {
        return new GoogleCalendarConnectionResponse(false, null, null, null);
    }
}
