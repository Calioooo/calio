package com.calio.calendar.integration.sync.operation.domain;

import java.time.Instant;
import java.util.Objects;

public final class GoogleCalendarEffectiveScope {

    private final GoogleCalendarEffectiveScopeType type;
    private final Long canonicalId;
    private final Instant originStartAt;

    private GoogleCalendarEffectiveScope(
            GoogleCalendarEffectiveScopeType type,
            Long canonicalId,
            Instant originStartAt
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.canonicalId = requirePositive(canonicalId);
        this.originStartAt = originStartAt;
    }

    public static GoogleCalendarEffectiveScope event(Long eventId) {
        return new GoogleCalendarEffectiveScope(GoogleCalendarEffectiveScopeType.EVENT, eventId, null);
    }

    public static GoogleCalendarEffectiveScope recurrenceEvent(Long recurrenceEventId) {
        return new GoogleCalendarEffectiveScope(
                GoogleCalendarEffectiveScopeType.RECURRENCE_EVENT,
                recurrenceEventId,
                null
        );
    }

    public static GoogleCalendarEffectiveScope recurrenceOverride(
            Long recurrenceEventId,
            Instant originStartAt
    ) {
        return new GoogleCalendarEffectiveScope(
                GoogleCalendarEffectiveScopeType.RECURRENCE_OVERRIDE,
                recurrenceEventId,
                Objects.requireNonNull(originStartAt, "originStartAt")
        );
    }

    public GoogleCalendarEffectiveScopeType type() {
        return type;
    }

    public Long canonicalId() {
        return canonicalId;
    }

    public boolean isRecurrenceEventAggregate() {
        return type == GoogleCalendarEffectiveScopeType.RECURRENCE_EVENT;
    }

    public String storedScope() {
        return type.getStoredValue();
    }

    public String storedKey() {
        if (type == GoogleCalendarEffectiveScopeType.RECURRENCE_OVERRIDE) {
            return canonicalId + ":" + originStartAt;
        }
        return canonicalId.toString();
    }

    public String childOverrideKeyPrefix() {
        if (!isRecurrenceEventAggregate()) {
            throw new IllegalStateException("Only recurrence-event scope has child overrides");
        }
        return canonicalId + ":";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GoogleCalendarEffectiveScope scope)) {
            return false;
        }
        return type == scope.type
                && canonicalId.equals(scope.canonicalId)
                && Objects.equals(originStartAt, scope.originStartAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, canonicalId, originStartAt);
    }

    private static Long requirePositive(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Canonical scope ID must be positive");
        }
        return value;
    }
}
