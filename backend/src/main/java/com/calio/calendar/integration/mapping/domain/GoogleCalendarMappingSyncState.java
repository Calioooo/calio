package com.calio.calendar.integration.mapping.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class GoogleCalendarMappingSyncState {

    public static final int STATUS_LENGTH = 32;
    public static final int CONTENT_HASH_LENGTH = 64;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = STATUS_LENGTH)
    private GoogleCalendarMappingSyncStatus status;

    @Column(name = "synced_content_hash", nullable = false, length = CONTENT_HASH_LENGTH)
    private String syncedContentHash;

    protected GoogleCalendarMappingSyncState() {
    }

    private GoogleCalendarMappingSyncState(String syncedContentHash) {
        status = GoogleCalendarMappingSyncStatus.ACTIVE;
        updateSyncedContentHash(syncedContentHash);
    }

    public static GoogleCalendarMappingSyncState active(String syncedContentHash) {
        return new GoogleCalendarMappingSyncState(syncedContentHash);
    }

    public void updateSyncedContentHash(String syncedContentHash) {
        this.syncedContentHash = syncedContentHash;
    }

    public void markConflicted() {
        status = GoogleCalendarMappingSyncStatus.CONFLICTED;
    }

    public GoogleCalendarMappingSyncStatus getStatus() {
        return status;
    }

    public String getSyncedContentHash() {
        return syncedContentHash;
    }

}
