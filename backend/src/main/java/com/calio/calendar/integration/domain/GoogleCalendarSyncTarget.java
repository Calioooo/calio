package com.calio.calendar.integration.domain;

import java.time.Instant;
import java.util.Objects;

public sealed interface GoogleCalendarSyncTarget permits
        GoogleCalendarSyncTarget.Event,
        GoogleCalendarSyncTarget.RecurrenceEvent,
        GoogleCalendarSyncTarget.RecurrenceOverride {

    String EVENT_TYPE = "GENERAL_EVENT";
    String RECURRENCE_EVENT_TYPE = "RECURRENCE_MASTER";
    String RECURRENCE_OVERRIDE_TYPE = "RECURRENCE_OVERRIDE";

    String storedType();

    String storedKey();

    default boolean includes(GoogleCalendarSyncTarget other) {
        if (equals(other)) {
            return true;
        }
        return this instanceof RecurrenceEvent recurrenceEvent
                && other instanceof RecurrenceOverride override
                && recurrenceEvent.recurrenceEventId().equals(override.recurrenceEventId());
    }

    static Event event(Long eventId) {
        return new Event(requireId(eventId));
    }

    static RecurrenceEvent recurrenceEvent(Long recurrenceEventId) {
        return new RecurrenceEvent(requireId(recurrenceEventId));
    }

    static RecurrenceOverride recurrenceOverride(
            Long recurrenceEventId,
            Instant originStartAt
    ) {
        return new RecurrenceOverride(
                requireId(recurrenceEventId),
                Objects.requireNonNull(originStartAt, "originStartAt")
        );
    }

    static String overrideKeyPrefix(Long recurrenceEventId) {
        return requireId(recurrenceEventId) + ":";
    }

    static GoogleCalendarSyncTarget fromStoredValues(String storedType, String storedKey) {
        return switch (storedType) {
            case EVENT_TYPE -> event(parseId(storedKey));
            case RECURRENCE_EVENT_TYPE -> recurrenceEvent(parseId(storedKey));
            case RECURRENCE_OVERRIDE_TYPE -> parseOverride(storedKey);
            default -> throw new IllegalArgumentException(
                    "Unsupported Google Calendar sync target type: " + storedType
            );
        };
    }

    private static RecurrenceOverride parseOverride(String storedKey) {
        int separator = storedKey.indexOf(':');
        if (separator <= 0 || separator == storedKey.length() - 1) {
            throw new IllegalArgumentException("Invalid recurrence override target key");
        }
        return recurrenceOverride(
                parseId(storedKey.substring(0, separator)),
                Instant.parse(storedKey.substring(separator + 1))
        );
    }

    private static Long requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Google Calendar sync target ID must be positive");
        }
        return id;
    }

    private static Long parseId(String encodedId) {
        try {
            return requireId(Long.valueOf(encodedId));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid Google Calendar sync target ID", exception);
        }
    }

    record Event(Long eventId) implements GoogleCalendarSyncTarget {
        @Override public String storedType() { return EVENT_TYPE; }
        @Override public String storedKey() { return eventId.toString(); }
    }

    record RecurrenceEvent(Long recurrenceEventId) implements GoogleCalendarSyncTarget {
        @Override public String storedType() { return RECURRENCE_EVENT_TYPE; }
        @Override public String storedKey() { return recurrenceEventId.toString(); }
    }

    record RecurrenceOverride(
            Long recurrenceEventId,
            Instant originStartAt
    ) implements GoogleCalendarSyncTarget {
        @Override public String storedType() { return RECURRENCE_OVERRIDE_TYPE; }
        @Override public String storedKey() {
            return overrideKeyPrefix(recurrenceEventId) + originStartAt;
        }
    }
}
