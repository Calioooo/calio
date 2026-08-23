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

    @Embedded
    private GoogleCalendarMappingSyncState syncState;

    @Column(name = "local_modified_at")
    private Instant localModifiedAt;

    protected GoogleCalendarRecurrenceOverrideMapping() {
    }

    public GoogleCalendarRecurrenceOverrideMapping(
            GoogleCalendarRecurrenceEventMapping recurrenceEventMapping,
            RecurrenceEventOverride recurrenceEventOverride,
            String externalEventId,
            String providerEtag
    ) {
        this.recurrenceEventMapping = recurrenceEventMapping;
        this.recurrenceEventOverride = recurrenceEventOverride;
        this.externalEventId = externalEventId;
        this.syncState = GoogleCalendarMappingSyncState.active(providerEtag);
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

    public void updateProviderEtag(String providerEtag) {
        syncState.updateProviderEtag(providerEtag);
    }

    public void markConflicted() {
        syncState.markConflicted();
    }

    public boolean isConflicted() {
        return syncState.isConflicted();
    }

    public String getProviderEtag() {
        return syncState.getProviderEtag();
    }

    public void markLocalModification(Instant modifiedAt) {
        localModifiedAt = modifiedAt;
    }

    public boolean hasLocalModification() {
        return localModifiedAt != null;
    }
}
