package com.calio.calendar.external.google.dto;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleCalendarEventPage(
        List<GoogleCalendarEventItem> items,
        String nextPageToken,
        String nextSyncToken,
        String timeZone
) {

    public GoogleCalendarEventPage {
        items = items == null ? List.of() : List.copyOf(items);
        if (hasText(nextPageToken) && hasText(nextSyncToken)) {
            throw invalidResponse();
        }
    }

    public boolean hasNextPage() {
        return hasText(nextPageToken);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static CalioException invalidResponse() {
        return new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
    }
}
