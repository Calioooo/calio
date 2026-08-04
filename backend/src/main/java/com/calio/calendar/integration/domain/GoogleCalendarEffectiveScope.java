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
                && master.externalMasterId().equals(override.externalMasterId());
    }

    static GeneralEvent generalEvent(String externalEventId) {
        return new GeneralEvent(requireIdentity(externalEventId));
    }

    static RecurrenceMaster recurrenceMaster(String externalMasterId) {
        return new RecurrenceMaster(requireIdentity(externalMasterId));
    }

    static RecurrenceOverride recurrenceOverride(
            String externalMasterId,
            Instant originStartAt
    ) {
        return new RecurrenceOverride(
                requireIdentity(externalMasterId),
                Objects.requireNonNull(originStartAt, "originStartAt")
        );
    }

    static String recurrenceOverrideKeyPrefix(String externalMasterId) {
        String masterId = requireIdentity(externalMasterId);
        return masterId.length() + ":" + masterId + ":";
    }

    static GoogleCalendarEffectiveScope decode(String type, String key) {
        return switch (type) {
            case GENERAL_EVENT -> generalEvent(key);
            case RECURRENCE_MASTER -> recurrenceMaster(key);
            case RECURRENCE_OVERRIDE -> decodeOverride(key);
            default -> throw new IllegalArgumentException("Unsupported effective scope: " + type);
        };
    }

    private static RecurrenceOverride decodeOverride(String key) {
        int separator = key.indexOf(':');
        if (separator <= 0) {
            throw new IllegalArgumentException("Invalid recurrence override scope key");
        }
        int masterLength = Integer.parseInt(key.substring(0, separator));
        int masterStart = separator + 1;
        int originSeparator = masterStart + masterLength;
        if (originSeparator >= key.length() || key.charAt(originSeparator) != ':') {
            throw new IllegalArgumentException("Invalid recurrence override scope key");
        }
        return recurrenceOverride(
                key.substring(masterStart, originSeparator),
                Instant.parse(key.substring(originSeparator + 1))
        );
    }

    private static String requireIdentity(String identity) {
        if (identity == null || identity.isBlank()) {
            throw new IllegalArgumentException("Provider identity is required");
        }
        return identity;
    }

    record GeneralEvent(String externalEventId) implements GoogleCalendarEffectiveScope {
        @Override public String encodedType() { return GENERAL_EVENT; }
        @Override public String encodedKey() { return externalEventId; }
    }

    record RecurrenceMaster(String externalMasterId) implements GoogleCalendarEffectiveScope {
        @Override public String encodedType() { return RECURRENCE_MASTER; }
        @Override public String encodedKey() { return externalMasterId; }
    }

    record RecurrenceOverride(
            String externalMasterId,
            Instant originStartAt
    ) implements GoogleCalendarEffectiveScope {
        @Override public String encodedType() { return RECURRENCE_OVERRIDE; }
        @Override public String encodedKey() {
            return recurrenceOverrideKeyPrefix(externalMasterId) + originStartAt;
        }
    }
}
