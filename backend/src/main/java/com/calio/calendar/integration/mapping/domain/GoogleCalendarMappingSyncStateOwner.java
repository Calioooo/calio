package com.calio.calendar.integration.mapping.domain;

public interface GoogleCalendarMappingSyncStateOwner {

    GoogleCalendarMappingSyncState getSyncState();

    default void updateSyncedContentHash(String syncedContentHash) {
        getSyncState().updateSyncedContentHash(syncedContentHash);
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

}
