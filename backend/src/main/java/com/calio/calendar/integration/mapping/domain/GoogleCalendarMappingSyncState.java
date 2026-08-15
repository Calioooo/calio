package com.calio.calendar.integration.mapping.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class GoogleCalendarMappingSyncState {

    public static final int STATUS_LENGTH = 32;
    public static final int PROVIDER_ETAG_LENGTH = 1024;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = STATUS_LENGTH)
    private GoogleCalendarMappingSyncStatus status;

    @Column(name = "provider_etag", nullable = false, length = PROVIDER_ETAG_LENGTH)
    private String providerEtag;

    protected GoogleCalendarMappingSyncState() {
    }

    private GoogleCalendarMappingSyncState(String providerEtag) {
        status = GoogleCalendarMappingSyncStatus.ACTIVE;
        updateProviderEtag(providerEtag);
    }

    public static GoogleCalendarMappingSyncState active(String providerEtag) {
        return new GoogleCalendarMappingSyncState(providerEtag);
    }

    public void updateProviderEtag(String providerEtag) {
        if (providerEtag == null || providerEtag.isBlank()) {
            throw new IllegalArgumentException("Google provider etag is required");
        }
        this.providerEtag = providerEtag;
    }

    public void markConflicted() {
        status = GoogleCalendarMappingSyncStatus.CONFLICTED;
    }

    public boolean isConflicted() {
        return status == GoogleCalendarMappingSyncStatus.CONFLICTED;
    }

    public String getProviderEtag() {
        return providerEtag;
    }

}
