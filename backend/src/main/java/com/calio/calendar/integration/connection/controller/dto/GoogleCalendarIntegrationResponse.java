package com.calio.calendar.integration.connection.controller.dto;

import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import java.time.Instant;

public record GoogleCalendarIntegrationResponse(
        boolean connected,
        String googleEmail,
        String googleSubject,
        Instant connectedAt
) {

    public static GoogleCalendarIntegrationResponse connected(GoogleCalendarConnection connection) {
        return new GoogleCalendarIntegrationResponse(
                true,
                connection.getGoogleEmail(),
                connection.getGoogleSubject(),
                connection.getConnectedAt()
        );
    }

    public static GoogleCalendarIntegrationResponse disconnected() {
        return new GoogleCalendarIntegrationResponse(false, null, null, null);
    }
}
