package com.calio.calendar.integration.mapping.domain;

import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Embedded;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

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
                        columnNames = {
                                "google_calendar_recurrence_event_mapping_id", "origin_start_at"
                        }
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

    @Column(name = "origin_start_at", nullable = false, updatable = false)
    private java.time.Instant originStartAt;

    @Column(name = "external_event_id", nullable = false, length = 1024)
    private String externalEventId;

    @Embedded
    private GoogleCalendarMappingSyncState syncState;

    @Column(name = "local_changed", nullable = false)
    private boolean localChanged;

    protected GoogleCalendarRecurrenceOverrideMapping() {
    }

    public GoogleCalendarRecurrenceOverrideMapping(
            GoogleCalendarRecurrenceEventMapping recurrenceEventMapping,
            java.time.Instant originStartAt,
            String externalEventId,
            String providerEtag
    ) {
        this.recurrenceEventMapping = recurrenceEventMapping;
        this.originStartAt = originStartAt;
        this.externalEventId = externalEventId;
        this.syncState = GoogleCalendarMappingSyncState.active(providerEtag);
    }

    public Long getId() {
        return id;
    }

    public GoogleCalendarRecurrenceEventMapping getRecurrenceEventMapping() {
        return recurrenceEventMapping;
    }

    public java.time.Instant getOriginStartAt() {
        return originStartAt;
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

    public GoogleCalendarRecurrenceOverrideMapping(
            GoogleCalendarRecurrenceEventMapping recurrenceEventMapping,
            RecurrenceEventOverride recurrenceEventOverride,
            String externalEventId,
            String providerEtag
    ) {
        this(recurrenceEventMapping, recurrenceEventOverride.getOriginStartAt(), externalEventId,
                providerEtag);
    }

    public void markLocalChanged() {
        localChanged = true;
    }

    public boolean isLocalChanged() {
        return localChanged;
    }

    public boolean isConflicted() {
        return syncState.isConflicted();
    }

    public String getProviderEtag() {
        return syncState.getProviderEtag();
    }
}
