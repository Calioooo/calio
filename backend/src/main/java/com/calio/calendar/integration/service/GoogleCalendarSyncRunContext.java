package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.service.GoogleCalendarRecurrenceMapper.RecurrenceEventResult;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class GoogleCalendarSyncRunContext {

    private static final int MAX_SEEN_IDENTITIES = 100_000;

    private String accessToken;
    private final Map<String, ParentOutcome> parentOutcomes = new HashMap<>();
    private final Set<String> seenGeneralEventIds = new HashSet<>();
    private final Set<String> seenRecurrenceMasterIds = new HashSet<>();
    private final Set<OverrideIdentity> seenRecurrenceOverrideIds = new HashSet<>();

    GoogleCalendarSyncRunContext(String accessToken) {
        this.accessToken = accessToken;
    }

    String accessToken() {
        return accessToken;
    }

    void replaceAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    ParentOutcome parentOutcome(String externalMasterId) {
        return parentOutcomes.get(externalMasterId);
    }

    void rememberParent(String externalMasterId, ParentOutcome outcome) {
        if (!parentOutcomes.containsKey(externalMasterId)
                && parentOutcomes.size() >= MAX_SEEN_IDENTITIES) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
        }
        parentOutcomes.put(externalMasterId, outcome);
    }

    void seeGeneral(String externalEventId) {
        addBounded(seenGeneralEventIds, externalEventId);
    }

    void seeMaster(String externalEventId) {
        addBounded(seenRecurrenceMasterIds, externalEventId);
    }

    void seeOverride(String parentExternalEventId, String externalEventId) {
        addBounded(
                seenRecurrenceOverrideIds,
                new OverrideIdentity(parentExternalEventId, externalEventId)
        );
    }

    Set<String> seenGeneralEventIds() {
        return Set.copyOf(seenGeneralEventIds);
    }

    Set<String> seenRecurrenceMasterIds() {
        return Set.copyOf(seenRecurrenceMasterIds);
    }

    Set<OverrideIdentity> seenRecurrenceOverrideIds() {
        return Set.copyOf(seenRecurrenceOverrideIds);
    }

    void resetSeenIdentities() {
        seenGeneralEventIds.clear();
        seenRecurrenceMasterIds.clear();
        seenRecurrenceOverrideIds.clear();
    }

    private <T> void addBounded(Set<T> identities, T identity) {
        if (!identities.contains(identity) && totalSeenCount() >= MAX_SEEN_IDENTITIES) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
        }
        identities.add(identity);
    }

    private int totalSeenCount() {
        return seenGeneralEventIds.size()
                + seenRecurrenceMasterIds.size()
                + seenRecurrenceOverrideIds.size();
    }

    sealed interface ParentOutcome permits ResolvedParent, MissingParent {
    }

    record ResolvedParent(RecurrenceEventResult result) implements ParentOutcome {
    }

    record OverrideIdentity(String parentExternalEventId, String externalEventId) {
    }

    enum MissingParent implements ParentOutcome {
        INSTANCE
    }
}
