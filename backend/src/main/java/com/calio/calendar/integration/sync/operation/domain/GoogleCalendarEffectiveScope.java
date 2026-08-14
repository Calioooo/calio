package com.calio.calendar.integration.sync.operation.domain;

import java.time.Instant;
import java.util.Objects;

public sealed interface GoogleCalendarEffectiveScope permits GoogleCalendarEffectiveScope.Event,
        GoogleCalendarEffectiveScope.RecurrenceEvent, GoogleCalendarEffectiveScope.RecurrenceOverride {

    GoogleCalendarEffectiveScopeType type();

    default String storedScope() {
        return type().getStoredValue();
    }

    String storedKey();

    default boolean includes(GoogleCalendarEffectiveScope other) {
        return equals(other)
                || this instanceof RecurrenceEvent recurrenceEvent
                && other instanceof RecurrenceOverride override
                && recurrenceEvent.recurrenceEventId().equals(override.recurrenceEventId());
    }

    static Event event(Long eventId) { return new Event(requirePositive(eventId)); }

    static RecurrenceEvent recurrenceEvent(Long recurrenceEventId) {
        return new RecurrenceEvent(requirePositive(recurrenceEventId));
    }

    static RecurrenceOverride recurrenceOverride(Long recurrenceEventId, Instant originStartAt) {
        return new RecurrenceOverride(requirePositive(recurrenceEventId),
                Objects.requireNonNull(originStartAt, "originStartAt"));
    }

    static String overrideKeyPrefix(Long recurrenceEventId) {
        return requirePositive(recurrenceEventId) + ":";
    }

    static GoogleCalendarEffectiveScope fromStoredValues(String scope, String key) {
        return switch (GoogleCalendarEffectiveScopeType.fromStoredValue(scope)) {
            case EVENT -> event(parseId(key));
            case RECURRENCE_EVENT -> recurrenceEvent(parseId(key));
            case RECURRENCE_OVERRIDE -> {
                int separator = key.indexOf(':');
                if (separator < 1 || separator == key.length() - 1) {
                    throw new IllegalArgumentException("Invalid recurrence override scope key");
                }
                yield recurrenceOverride(parseId(key.substring(0, separator)),
                        Instant.parse(key.substring(separator + 1)));
            }
        };
    }

    private static Long requirePositive(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Canonical scope ID must be positive");
        }
        return value;
    }

    private static Long parseId(String value) {
        try {
            return requirePositive(Long.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid canonical scope ID", exception);
        }
    }

    record Event(Long eventId) implements GoogleCalendarEffectiveScope {
        @Override public GoogleCalendarEffectiveScopeType type() {
            return GoogleCalendarEffectiveScopeType.EVENT;
        }
        @Override public String storedKey() { return eventId.toString(); }
    }

    record RecurrenceEvent(Long recurrenceEventId) implements GoogleCalendarEffectiveScope {
        @Override public GoogleCalendarEffectiveScopeType type() {
            return GoogleCalendarEffectiveScopeType.RECURRENCE_EVENT;
        }
        @Override public String storedKey() { return recurrenceEventId.toString(); }
    }

    record RecurrenceOverride(Long recurrenceEventId, Instant originStartAt)
            implements GoogleCalendarEffectiveScope {
        @Override public GoogleCalendarEffectiveScopeType type() {
            return GoogleCalendarEffectiveScopeType.RECURRENCE_OVERRIDE;
        }
        @Override public String storedKey() { return overrideKeyPrefix(recurrenceEventId) + originStartAt; }
    }
}
