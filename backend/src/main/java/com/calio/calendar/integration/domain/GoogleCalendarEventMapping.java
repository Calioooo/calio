package com.calio.calendar.integration.domain;

import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.event.domain.Event;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "google_calendar_event_mappings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_google_calendar_mapping_external_identity",
                        columnNames = {"integration_id", "calendar_key", "external_event_id"}
                ),
                @UniqueConstraint(
                        name = "uk_google_calendar_mapping_event_id",
                        columnNames = "event_id"
                )
        }
)
public class GoogleCalendarEventMapping extends BaseEntity {

    public static final String PRIMARY_CALENDAR_KEY = "primary";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "integration_id", nullable = false)
    private GoogleCalendarIntegration integration;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

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
    private GoogleCalendarMappingSyncStatus syncStatus;

    @Column(name = "synced_content_hash", nullable = false, length = 67)
    private String syncedContentHash;

    protected GoogleCalendarEventMapping() {
    }

    public GoogleCalendarEventMapping(
            GoogleCalendarIntegration integration,
            Event event,
            String externalEventId,
            GoogleCalendarItemSnapshot googleSnapshot
    ) {
        this.integration = integration;
        this.event = event;
        this.calendarKey = PRIMARY_CALENDAR_KEY;
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

    public GoogleCalendarIntegration getIntegration() {
        return integration;
    }

    public Event getEvent() {
        return event;
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

    public GoogleCalendarMappingSyncStatus getSyncStatus() { return syncStatus; }
    public String getSyncedContentHash() { return syncedContentHash; }
}
