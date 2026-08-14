package com.calio.calendar.integration.mapping.domain;

public interface GoogleCalendarMappingSyncStateOwner {

    GoogleCalendarMappingSyncState getSyncState();

    default void updateProviderObservation(GoogleProviderObservation observation) {
        getSyncState().updateProviderObservation(observation);
    }

    default void markConflicted() {
        getSyncState().markConflicted();
    }

    default GoogleCalendarMappingSyncStatus getSyncStatus() {
        return getSyncState().getStatus();
    }

    default String getSyncedContentHash() {
        return getSyncState().getSyncedContentHash();
    }

    default String getProviderEtag() {
        return getSyncState().getProviderEtag();
    }

    default java.time.Instant getProviderUpdatedAt() {
        return getSyncState().getProviderUpdatedAt();
    }
}
