package com.calio.calendar.external.google.dto;

import com.calio.calendar.integration.sync.operation.dto.GoogleEventJobPayload;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.ZoneOffset;

public record GoogleCalendarEventWriteRequest(
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String id,
        String summary,
        String description,
        GoogleCalendarEventTimeResponse start,
        GoogleCalendarEventTimeResponse end
) {
    public static GoogleCalendarEventWriteRequest from(GoogleEventJobPayload payload) {
        return from(payload, null);
    }

    public static GoogleCalendarEventWriteRequest forCreate(
            GoogleEventJobPayload payload,
            String providerIdentity
    ) {
        return from(payload, providerIdentity);
    }

    private static GoogleCalendarEventWriteRequest from(
            GoogleEventJobPayload payload,
            String providerIdentity
    ) {
        if (payload.allDay()) {
            return new GoogleCalendarEventWriteRequest(
                    providerIdentity, payload.title(), payload.description(),
                    new GoogleCalendarEventTimeResponse(
                            payload.startAt().atOffset(ZoneOffset.UTC).toLocalDate().toString(), null, null),
                    new GoogleCalendarEventTimeResponse(
                            payload.endAt().atOffset(ZoneOffset.UTC).toLocalDate().toString(), null, null)
            );
        }
        return new GoogleCalendarEventWriteRequest(
                providerIdentity, payload.title(), payload.description(),
                new GoogleCalendarEventTimeResponse(null, payload.startAt().toString(), payload.timeZone()),
                new GoogleCalendarEventTimeResponse(null, payload.endAt().toString(), payload.timeZone())
        );
    }
}
