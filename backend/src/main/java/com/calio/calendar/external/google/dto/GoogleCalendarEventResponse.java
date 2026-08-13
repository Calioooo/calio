package com.calio.calendar.external.google.dto;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleCalendarEventResponse(
        String id,
        String status,
        String etag,
        @JsonProperty("updated") Instant updatedAt,
        String summary,
        String description,
        List<String> recurrence,
        String recurringEventId,
        GoogleCalendarEventTimeResponse originalStartTime,
        GoogleCalendarEventTimeResponse start,
        GoogleCalendarEventTimeResponse end
) {

    public GoogleCalendarEventResponse {
        recurrence = recurrence == null
                ? List.of()
                : List.copyOf(recurrence);
        if (!hasText(id) || (!"cancelled".equals(status) && !hasValidSchedulePair(start, end))) {
            throw invalidResponse();
        }
    }

    public GoogleCalendarEventResponse(
            String id,
            String status,
            String etag,
            Instant updatedAt,
            String summary,
            String description,
            List<String> recurrence,
            String recurringEventId,
            GoogleCalendarEventTimeResponse start,
            GoogleCalendarEventTimeResponse end
    ) {
        this(
                id,
                status,
                etag,
                updatedAt,
                summary,
                description,
                recurrence,
                recurringEventId,
                null,
                start,
                end
        );
    }

    public boolean isCancelled() {
        return "cancelled".equals(status);
    }

    public boolean isRecurring() {
        return isRecurrenceEvent() || isRecurrenceOverride();
    }

    public boolean isRecurrenceEvent() {
        return !recurrence.isEmpty();
    }

    public boolean isRecurrenceOverride() {
        return hasText(recurringEventId);
    }

    private static boolean hasValidSchedulePair(
            GoogleCalendarEventTimeResponse start,
            GoogleCalendarEventTimeResponse end
    ) {
        if (start == null || end == null) {
            return false;
        }
        return (start.isAllDay() && end.isAllDay()) || (start.isTimed() && end.isTimed());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static CalioException invalidResponse() {
        return new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
    }
}
