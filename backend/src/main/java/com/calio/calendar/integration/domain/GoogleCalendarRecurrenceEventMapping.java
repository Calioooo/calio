package com.calio.calendar.integration.domain;

import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "google_calendar_recurrence_event_mappings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_google_calendar_recurrence_event_external",
                        columnNames = {"integration_id", "calendar_key", "external_event_id"}
                ),
                @UniqueConstraint(
                        name = "uk_google_calendar_recurrence_event_canonical",
                        columnNames = "recurrence_event_id"
                )
        }
)
public class GoogleCalendarRecurrenceEventMapping extends BaseEntity {

    public static final String PRIMARY_CALENDAR_KEY = "primary";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "integration_id", nullable = false)
    private GoogleCalendarIntegration integration;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurrence_event_id")
    private RecurrenceEvent recurrenceEvent;

    @Column(name = "calendar_key", nullable = false, length = 32)
    private String calendarKey;

    @Column(name = "external_event_id", nullable = false, length = 1024)
    private String externalEventId;

    @Column(name = "provider_etag", length = 1024)
    private String providerEtag;

    @Column(name = "provider_updated_at")
    private Instant providerUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 32)
    private GoogleCalendarMappingStatus syncStatus;

    @Column(name = "synced_content_hash", length = 64)
    private String syncedContentHash;

    @Column(name = "synced_content_hash_version", length = 32)
    private String syncedContentHashVersion;

    @Column(name = "local_deleted", nullable = false)
    private boolean localDeleted;

    protected GoogleCalendarRecurrenceEventMapping() {
    }

    public GoogleCalendarRecurrenceEventMapping(
            GoogleCalendarIntegration integration,
            RecurrenceEvent recurrenceEvent,
            String externalEventId,
            String providerEtag,
            Instant providerUpdatedAt
    ) {
        this.integration = integration;
        this.recurrenceEvent = recurrenceEvent;
        this.calendarKey = PRIMARY_CALENDAR_KEY;
        this.externalEventId = externalEventId;
        this.syncStatus = GoogleCalendarMappingStatus.ACTIVE;
        this.providerEtag = providerEtag;
        this.providerUpdatedAt = providerUpdatedAt;
    }

    public void updateProviderVersion(String providerEtag, Instant providerUpdatedAt) {
        this.providerEtag = providerEtag;
        this.providerUpdatedAt = providerUpdatedAt;
    }

    public void updateBaseline(
            String providerEtag,
            Instant providerUpdatedAt,
            String hashVersion,
            String contentHash
    ) {
        updateProviderVersion(providerEtag, providerUpdatedAt);
        this.syncedContentHashVersion = hashVersion;
        this.syncedContentHash = contentHash;
    }

    public void markConflicted() { this.syncStatus = GoogleCalendarMappingStatus.CONFLICTED; }
    public void detachLocalDeletion() { this.recurrenceEvent = null; this.localDeleted = true; }

    public Long getId() {
        return id;
    }

    public GoogleCalendarIntegration getIntegration() {
        return integration;
    }

    public RecurrenceEvent getRecurrenceEvent() {
        return recurrenceEvent;
    }

    public String getCalendarKey() {
        return calendarKey;
    }

    public String getExternalEventId() {
        return externalEventId;
    }

    public String getProviderEtag() {
        return providerEtag;
    }

    public Instant getProviderUpdatedAt() {
        return providerUpdatedAt;
    }

    public GoogleCalendarMappingStatus getSyncStatus() { return syncStatus; }
    public String getSyncedContentHash() { return syncedContentHash; }
    public String getSyncedContentHashVersion() { return syncedContentHashVersion; }
    public boolean isLocalDeleted() { return localDeleted; }
}
