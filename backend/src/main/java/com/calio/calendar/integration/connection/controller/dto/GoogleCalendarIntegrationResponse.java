package com.calio.calendar.integration.connection.controller.dto;

import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import java.time.Instant;

public record GoogleCalendarIntegrationResponse(
        boolean connected,
        String googleEmail,
        String googleSubject,
        Instant connectedAt
) {

    public static GoogleCalendarIntegrationResponse connected(GoogleCalendarIntegration integration) {
        return new GoogleCalendarIntegrationResponse(
                true,
                integration.getGoogleEmail(),
                integration.getGoogleSubject(),
                integration.getConnectedAt()
        );
    }

    public static GoogleCalendarIntegrationResponse disconnected() {
        return new GoogleCalendarIntegrationResponse(false, null, null, null);
    }
}
