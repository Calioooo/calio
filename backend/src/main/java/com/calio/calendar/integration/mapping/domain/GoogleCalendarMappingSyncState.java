package com.calio.calendar.integration.mapping.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;

@Embeddable
public class GoogleCalendarMappingSyncState {

    public static final int STATUS_LENGTH = 32;
    public static final int CONTENT_HASH_LENGTH = 64;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = STATUS_LENGTH)
    private GoogleCalendarMappingSyncStatus status;

    @Column(name = "synced_content_hash", nullable = false, length = CONTENT_HASH_LENGTH)
    private String syncedContentHash;

    @Column(name = "provider_etag", length = 1024)
    private String providerEtag;

    @Column(name = "provider_updated_at")
    private Instant providerUpdatedAt;

    protected GoogleCalendarMappingSyncState() {
    }

    private GoogleCalendarMappingSyncState(GoogleProviderObservation observation) {
        status = GoogleCalendarMappingSyncStatus.ACTIVE;
        updateProviderObservation(observation);
    }

    public static GoogleCalendarMappingSyncState active(GoogleProviderObservation observation) {
        return new GoogleCalendarMappingSyncState(observation);
    }

    public void updateProviderObservation(GoogleProviderObservation observation) {
        providerEtag = observation.etag();
        providerUpdatedAt = observation.updatedAt();
        syncedContentHash = observation.contentHash();
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

    public String getProviderEtag() {
        return providerEtag;
    }

    public Instant getProviderUpdatedAt() {
        return providerUpdatedAt;
    }
}
