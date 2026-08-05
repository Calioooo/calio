package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceEventUpsert;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class GoogleCalendarSyncRunContext {

    private static final int MAX_SEEN_IDENTITIES = 100_000;

    private String accessToken;
    private final Map<String, RecurrenceEventLookup> recurrenceEventLookups = new HashMap<>();
    private final Set<String> seenEventIds = new HashSet<>();
    private final Set<String> seenRecurrenceEventIds = new HashSet<>();
    private final Set<RecurrenceEventOverrideExternalKey> seenRecurrenceEventOverrideKeys =
            new HashSet<>();

    GoogleCalendarSyncRunContext(String accessToken) {
        this.accessToken = accessToken;
    }

    String accessToken() {
        return accessToken;
    }

    void replaceAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    RecurrenceEventLookup recurrenceEventLookup(String recurrenceEventExternalId) {
        return recurrenceEventLookups.get(recurrenceEventExternalId);
    }

    void rememberRecurrenceEvent(
            String recurrenceEventExternalId,
            RecurrenceEventLookup lookup
    ) {
        if (!recurrenceEventLookups.containsKey(recurrenceEventExternalId)
                && recurrenceEventLookups.size() >= MAX_SEEN_IDENTITIES) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
        }
        recurrenceEventLookups.put(recurrenceEventExternalId, lookup);
    }

    void seeEvent(String externalEventId) {
        addBounded(seenEventIds, externalEventId);
    }

    void seeRecurrenceEvent(String externalEventId) {
        addBounded(seenRecurrenceEventIds, externalEventId);
    }

    void seeRecurrenceEventOverride(
            String recurrenceEventExternalId,
            String overrideExternalEventId
    ) {
        addBounded(
                seenRecurrenceEventOverrideKeys,
                new RecurrenceEventOverrideExternalKey(
                        recurrenceEventExternalId,
                        overrideExternalEventId
                )
        );
    }

    Set<String> seenEventIds() {
        return Set.copyOf(seenEventIds);
    }

    Set<String> seenRecurrenceEventIds() {
        return Set.copyOf(seenRecurrenceEventIds);
    }

    Set<RecurrenceEventOverrideExternalKey> seenRecurrenceEventOverrideIds() {
        return Set.copyOf(seenRecurrenceEventOverrideKeys);
    }

    void resetSeenIdentities() {
        seenEventIds.clear();
        seenRecurrenceEventIds.clear();
        seenRecurrenceEventOverrideKeys.clear();
    }

    private <T> void addBounded(Set<T> identities, T identity) {
        if (!identities.contains(identity) && totalSeenCount() >= MAX_SEEN_IDENTITIES) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
        }
        identities.add(identity);
    }

    private int totalSeenCount() {
        return seenEventIds.size()
                + seenRecurrenceEventIds.size()
                + seenRecurrenceEventOverrideKeys.size();
    }

    sealed interface RecurrenceEventLookup
            permits FoundRecurrenceEvent, MissingRecurrenceEvent {
    }

    record FoundRecurrenceEvent(RecurrenceEventUpsert recurrenceEvent)
            implements RecurrenceEventLookup {
    }

    record RecurrenceEventOverrideExternalKey(
            String recurrenceEventExternalId,
            String overrideExternalEventId
    ) {
    }

    enum MissingRecurrenceEvent implements RecurrenceEventLookup {
        INSTANCE
    }
}
