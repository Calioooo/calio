package com.calio.calendar.external.google.dto;

import com.calio.calendar.integration.sync.operation.dto.GoogleEventJobPayload;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceJobPayload;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceOverrideJobPayload;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.ZoneOffset;
import java.util.List;

public record GoogleCalendarEventWriteRequest(
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String id,
        String summary,
        String description,
        GoogleCalendarEventTimeResponse start,
        GoogleCalendarEventTimeResponse end,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<String> recurrence
) {
    public static GoogleCalendarEventWriteRequest forUpdate(GoogleEventJobPayload payload) {
        return from(payload, null);
    }

    public static GoogleCalendarEventWriteRequest forCreate(
            GoogleEventJobPayload payload,
            String providerIdentity
    ) {
        return from(payload, providerIdentity);
    }

    public static GoogleCalendarEventWriteRequest forRecurrenceCreate(
            GoogleRecurrenceJobPayload payload,
            String providerIdentity
    ) {
        return from(payload.title(), payload.description(), payload.startAt(), payload.endAt(),
                payload.allDay(), payload.timeZone(), providerIdentity, payload.recurrence());
    }

    public static GoogleCalendarEventWriteRequest forRecurrenceUpdate(
            GoogleRecurrenceJobPayload payload
    ) {
        return from(payload.title(), payload.description(), payload.startAt(), payload.endAt(),
                payload.allDay(), payload.timeZone(), null, payload.recurrence());
    }

    public static GoogleCalendarEventWriteRequest forOverrideUpdate(
            GoogleRecurrenceOverrideJobPayload payload
    ) {
        return from(payload.title(), payload.description(), payload.startAt(), payload.endAt(),
                payload.allDay(), payload.timeZone(), null, null);
    }

    private static GoogleCalendarEventWriteRequest from(
            GoogleEventJobPayload payload,
            String providerIdentity
    ) {
        return from(payload.title(), payload.description(), payload.startAt(), payload.endAt(),
                payload.allDay(), payload.timeZone(), providerIdentity, null);
    }

    private static GoogleCalendarEventWriteRequest from(
            String title, String description, java.time.Instant startAt, java.time.Instant endAt,
            boolean allDay, String timeZone, String providerIdentity, List<String> recurrence
    ) {
        if (allDay) {
            return new GoogleCalendarEventWriteRequest(
                    providerIdentity, title, description,
                    new GoogleCalendarEventTimeResponse(
                            startAt.atOffset(ZoneOffset.UTC).toLocalDate().toString(), null, null),
                    new GoogleCalendarEventTimeResponse(
                            endAt.atOffset(ZoneOffset.UTC).toLocalDate().toString(), null, null),
                    recurrence
            );
        }
        return new GoogleCalendarEventWriteRequest(
                providerIdentity, title, description,
                new GoogleCalendarEventTimeResponse(null, startAt.toString(), timeZone),
                new GoogleCalendarEventTimeResponse(null, endAt.toString(), timeZone),
                recurrence
        );
    }
}
