package com.calio.calendar.external.google.dto;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleCalendarEventItem(
        String id,
        String status,
        String etag,
        @JsonProperty("updated") Instant updatedAt,
        String summary,
        String description,
        List<String> recurrence,
        String recurringEventId,
        GoogleCalendarEventTime originalStartTime,
        GoogleCalendarEventTime start,
        GoogleCalendarEventTime end
) {

    public GoogleCalendarEventItem {
        recurrence = recurrence == null
                ? List.of()
                : List.copyOf(recurrence);
        if (!hasText(id) || (!"cancelled".equals(status) && !hasValidSchedulePair(start, end))) {
            throw invalidResponse();
        }
    }

    public GoogleCalendarEventItem(
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
        return isRecurrenceMaster() || isRecurrenceOccurrence();
    }

    public boolean isRecurrenceMaster() {
        return !recurrence.isEmpty();
    }

    public boolean isRecurrenceOccurrence() {
        return hasText(recurringEventId);
    }

    private static boolean hasValidSchedulePair(
            GoogleCalendarEventTime start,
            GoogleCalendarEventTime end
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
