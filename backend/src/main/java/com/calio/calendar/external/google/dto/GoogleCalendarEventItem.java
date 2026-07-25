package com.calio.calendar.external.google.dto;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

public record GoogleCalendarEventItem(
        String id,
        String status,
        String etag,
        Instant updatedAt,
        String summary,
        String description,
        List<String> recurrence,
        String recurringEventId,
        GoogleCalendarEventTime start,
        GoogleCalendarEventTime end
) {

    public boolean isCancelled() {
        return "cancelled".equals(status);
    }

    public boolean isRecurring() {
        return recurringEventId != null || !recurrence.isEmpty();
    }

    static Instant parseUpdatedAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new CalioException(
                    ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID,
                    exception
            );
        }
    }
}
