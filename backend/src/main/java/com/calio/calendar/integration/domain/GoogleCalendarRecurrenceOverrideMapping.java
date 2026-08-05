package com.calio.calendar.integration.domain;

import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
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
        name = "google_calendar_recurrence_override_mappings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_google_calendar_recurrence_override_external",
                        columnNames = {
                                "google_calendar_recurrence_event_mapping_id",
                                "external_event_id"
                        }
                ),
                @UniqueConstraint(
                        name = "uk_google_calendar_recurrence_override_canonical",
                        columnNames = "recurrence_event_override_id"
                )
        }
)
public class GoogleCalendarRecurrenceOverrideMapping extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "google_calendar_recurrence_event_mapping_id", nullable = false)
    private GoogleCalendarRecurrenceEventMapping recurrenceEventMapping;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recurrence_event_override_id", nullable = false)
    private RecurrenceEventOverride recurrenceEventOverride;

    @Column(name = "external_event_id", nullable = false, length = 1024)
    private String externalEventId;

    @Column(name = "provider_etag", length = 1024)
    private String providerEtag;

    @Column(name = "provider_updated_at")
    private Instant providerUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 32)
    private GoogleCalendarMappingSyncStatus syncStatus;

    @Column(name = "synced_content_hash", nullable = false, length = 67)
    private String syncedContentHash;

    protected GoogleCalendarRecurrenceOverrideMapping() {
    }

    public GoogleCalendarRecurrenceOverrideMapping(
            GoogleCalendarRecurrenceEventMapping recurrenceEventMapping,
            RecurrenceEventOverride recurrenceEventOverride,
            String externalEventId,
            GoogleCalendarItemSnapshot googleSnapshot
    ) {
        this.recurrenceEventMapping = recurrenceEventMapping;
        this.recurrenceEventOverride = recurrenceEventOverride;
        this.externalEventId = externalEventId;
        this.syncStatus = GoogleCalendarMappingSyncStatus.ACTIVE;
        updateGoogleSnapshot(googleSnapshot);
    }

    public void updateGoogleSnapshot(GoogleCalendarItemSnapshot googleSnapshot) {
        this.providerEtag = googleSnapshot.etag();
        this.providerUpdatedAt = googleSnapshot.updatedAt();
        this.syncedContentHash = googleSnapshot.contentHash();
    }

    public void markConflicted() {
        this.syncStatus = GoogleCalendarMappingSyncStatus.CONFLICTED;
    }

    public Long getId() {
        return id;
    }

    public GoogleCalendarRecurrenceEventMapping getRecurrenceEventMapping() {
        return recurrenceEventMapping;
    }

    public RecurrenceEventOverride getRecurrenceEventOverride() {
        return recurrenceEventOverride;
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

    public GoogleCalendarMappingSyncStatus getSyncStatus() { return syncStatus; }
    public String getSyncedContentHash() { return syncedContentHash; }
}
