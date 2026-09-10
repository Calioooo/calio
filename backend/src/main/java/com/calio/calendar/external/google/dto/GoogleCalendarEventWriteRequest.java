package com.calio.calendar.external.google.dto;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.sync.operation.dto.GoogleEventJobPayload;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceJobPayload;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceOverrideJobPayload;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

public record GoogleCalendarEventWriteRequest(
        @JsonInclude(NON_NULL)
        String id,
        String summary,
        String description,
        GoogleCalendarEventTimeResponse start,
        GoogleCalendarEventTimeResponse end,
        @JsonInclude(NON_NULL)
        List<String> recurrence
) {
    public static GoogleCalendarEventWriteRequest forEventUpdate(GoogleEventJobPayload payload) {
        if (payload == null) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID);
        }
        return from(
                payload.title(),
                payload.description(),
                payload.startAt(),
                payload.endAt(),
                payload.allDay(),
                payload.timeZone(),
                null,
                null
        );
    }

    public static GoogleCalendarEventWriteRequest forEventCreate(
            GoogleEventJobPayload payload,
            String providerIdentity
    ) {
        if (!hasText(providerIdentity) || payload == null) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID);
        }
        return from(
                payload.title(),
                payload.description(),
                payload.startAt(),
                payload.endAt(),
                payload.allDay(),
                payload.timeZone(),
                providerIdentity,
                null
        );
    }

    public static GoogleCalendarEventWriteRequest forRecurrenceCreate(
            GoogleRecurrenceJobPayload payload,
            String providerIdentity
    ) {
        if (!hasText(providerIdentity) || payload == null) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID);
        }
        return from(
                payload.title(),
                payload.description(),
                payload.startAt(),
                payload.endAt(),
                payload.allDay(),
                payload.timeZone(),
                providerIdentity,
                payload.recurrence()
        );
    }

    public static GoogleCalendarEventWriteRequest forRecurrenceUpdate(
            GoogleRecurrenceJobPayload payload
    ) {
        if (payload == null) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID);
        }
        return from(
                payload.title(),
                payload.description(),
                payload.startAt(),
                payload.endAt(),
                payload.allDay(),
                payload.timeZone(),
                null,
                payload.recurrence()
        );
    }

    public static GoogleCalendarEventWriteRequest forOverrideUpdate(
            GoogleRecurrenceOverrideJobPayload payload
    ) {
        if (payload == null) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID);
        }
        return from(
                payload.title(),
                payload.description(),
                payload.startAt(),
                payload.endAt(),
                payload.allDay(),
                payload.timeZone(),
                null,
                null
        );
    }

    private static GoogleCalendarEventWriteRequest from(
            String title, String description, Instant startAt, Instant endAt,
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
