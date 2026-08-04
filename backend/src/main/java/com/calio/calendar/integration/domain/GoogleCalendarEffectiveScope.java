package com.calio.calendar.integration.domain;

import java.time.Instant;
import java.util.Objects;

public sealed interface GoogleCalendarEffectiveScope permits
        GoogleCalendarEffectiveScope.GeneralEvent,
        GoogleCalendarEffectiveScope.RecurrenceMaster,
        GoogleCalendarEffectiveScope.RecurrenceOverride {

    String GENERAL_EVENT = "GENERAL_EVENT";
    String RECURRENCE_MASTER = "RECURRENCE_MASTER";
    String RECURRENCE_OVERRIDE = "RECURRENCE_OVERRIDE";

    String encodedType();

    String encodedKey();

    default boolean covers(GoogleCalendarEffectiveScope other) {
        if (equals(other)) {
            return true;
        }
        return this instanceof RecurrenceMaster master
                && other instanceof RecurrenceOverride override
                && master.recurrenceEventId().equals(override.recurrenceEventId());
    }

    static GeneralEvent generalEvent(Long eventId) {
        return new GeneralEvent(requireId(eventId));
    }

    static RecurrenceMaster recurrenceMaster(Long recurrenceEventId) {
        return new RecurrenceMaster(requireId(recurrenceEventId));
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

    static String recurrenceOverrideKeyPrefix(Long recurrenceEventId) {
        return requireId(recurrenceEventId) + ":";
    }

    static GoogleCalendarEffectiveScope decode(String type, String key) {
        return switch (type) {
            case GENERAL_EVENT -> generalEvent(parseId(key));
            case RECURRENCE_MASTER -> recurrenceMaster(parseId(key));
            case RECURRENCE_OVERRIDE -> decodeOverride(key);
            default -> throw new IllegalArgumentException("Unsupported effective scope: " + type);
        };
    }

    private static RecurrenceOverride decodeOverride(String key) {
        int separator = key.indexOf(':');
        if (separator <= 0 || separator == key.length() - 1) {
            throw new IllegalArgumentException("Invalid recurrence override scope key");
        }
        return recurrenceOverride(
                parseId(key.substring(0, separator)),
                Instant.parse(key.substring(separator + 1))
        );
    }

    private static Long requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Canonical resource ID must be positive");
        }
        return id;
    }

    private static Long parseId(String encodedId) {
        try {
            return requireId(Long.valueOf(encodedId));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid canonical resource ID", exception);
        }
    }

    record GeneralEvent(Long eventId) implements GoogleCalendarEffectiveScope {
        @Override public String encodedType() { return GENERAL_EVENT; }
        @Override public String encodedKey() { return eventId.toString(); }
    }

    record RecurrenceMaster(Long recurrenceEventId) implements GoogleCalendarEffectiveScope {
        @Override public String encodedType() { return RECURRENCE_MASTER; }
        @Override public String encodedKey() { return recurrenceEventId.toString(); }
    }

    record RecurrenceOverride(
            Long recurrenceEventId,
            Instant originStartAt
    ) implements GoogleCalendarEffectiveScope {
        @Override public String encodedType() { return RECURRENCE_OVERRIDE; }
        @Override public String encodedKey() {
            return recurrenceOverrideKeyPrefix(recurrenceEventId) + originStartAt;
        }
    }
}
