package com.calio.calendar.integration.mapping.domain;

import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Embedded;
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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(name = "local_deleted_at")
    private Instant localDeletedAt;

    @Column(name = "local_modified_at")
    private Instant localModifiedAt;

    @Column(name = "calendar_key", nullable = false, length = 32)
    private String calendarKey;

    @Column(name = "external_event_id", nullable = false, length = 1024)
    private String externalEventId;

    @Embedded
    private GoogleCalendarMappingSyncState syncState;

    protected GoogleCalendarEventMapping() {
    }

    public GoogleCalendarEventMapping(
            GoogleCalendarIntegration integration,
            Event event,
            String externalEventId,
            String providerEtag
    ) {
        this.integration = integration;
        this.event = event;
        this.calendarKey = PRIMARY_CALENDAR_KEY;
        this.externalEventId = externalEventId;
        this.syncState = GoogleCalendarMappingSyncState.active(providerEtag);
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

    public String getExternalEventId() {
        return externalEventId;
    }

    public void updateProviderEtag(String providerEtag) {
        syncState.updateProviderEtag(providerEtag);
    }

    public void markConflicted() {
        syncState.markConflicted();
    }

    public boolean isConflicted() {
        return syncState.isConflicted();
    }

    public boolean blocksLocalMutation() {
        return integration.isConnected() && !isConflicted();
    }

    public String getProviderEtag() {
        return syncState.getProviderEtag();
    }

    public boolean hasCanonicalEvent() {
        return event != null;
    }

    public boolean canApplyGoogleChange() {
        return !isConflicted() && hasCanonicalEvent();
    }

    public void detachCanonicalEvent(Instant deletedAt) {
        event = null;
        localDeletedAt = deletedAt;
    }

    public void markLocalModification(Instant modifiedAt) {
        localModifiedAt = modifiedAt;
    }

    public boolean hasLocalModification() {
        return localModifiedAt != null;
    }

    public Instant getLocalDeletedAt() {
        return localDeletedAt;
    }
}
