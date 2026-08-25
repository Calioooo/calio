package com.calio.calendar.integration.mapping.domain;

import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
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

    protected GoogleCalendarRecurrenceEventMapping() {
    }

    public GoogleCalendarRecurrenceEventMapping(
            GoogleCalendarIntegration integration,
            RecurrenceEvent recurrenceEvent,
            String externalEventId,
            String providerEtag
    ) {
        this.integration = integration;
        this.recurrenceEvent = recurrenceEvent;
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

    public RecurrenceEvent getRecurrenceEvent() {
        return recurrenceEvent;
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

    public boolean shouldTrackLocalModification() {
        return !isConflicted() && !integration.isConnected();
    }

    public boolean hasCanonicalRecurrenceEvent() {
        return recurrenceEvent != null;
    }

    public boolean canApplyGoogleChange() {
        return !isConflicted() && hasCanonicalRecurrenceEvent();
    }

    public GoogleCalendarProviderChangeAction evaluateGoogleUpsert(String providerEtag) {
        if (isConflicted()) {
            return GoogleCalendarProviderChangeAction.IGNORE;
        }
        if (!hasCanonicalRecurrenceEvent() || hasLocalModification()) {
            return hasSameProviderEtag(providerEtag)
                    ? GoogleCalendarProviderChangeAction.IGNORE
                    : GoogleCalendarProviderChangeAction.MARK_CONFLICT;
        }
        return hasSameProviderEtag(providerEtag)
                ? GoogleCalendarProviderChangeAction.IGNORE
                : GoogleCalendarProviderChangeAction.APPLY;
    }

    public GoogleCalendarProviderChangeAction evaluateGoogleCancellation() {
        if (!canApplyGoogleChange()) {
            return GoogleCalendarProviderChangeAction.IGNORE;
        }
        return hasLocalModification()
                ? GoogleCalendarProviderChangeAction.MARK_CONFLICT
                : GoogleCalendarProviderChangeAction.APPLY;
    }

    public GoogleCalendarProviderChangeAction evaluateGoogleOverride() {
        if (isConflicted()) {
            return GoogleCalendarProviderChangeAction.IGNORE;
        }
        return hasCanonicalRecurrenceEvent()
                ? GoogleCalendarProviderChangeAction.APPLY
                : GoogleCalendarProviderChangeAction.MARK_CONFLICT;
    }

    public GoogleCalendarProviderChangeAction evaluateUnseenProviderRemoval(
            boolean hasPendingOutboundJob
    ) {
        if (!canApplyGoogleChange()) {
            return GoogleCalendarProviderChangeAction.IGNORE;
        }
        return hasLocalModification() || hasPendingOutboundJob
                ? GoogleCalendarProviderChangeAction.MARK_CONFLICT
                : GoogleCalendarProviderChangeAction.APPLY;
    }

    private boolean hasSameProviderEtag(String providerEtag) {
        return syncState.getProviderEtag().equals(providerEtag);
    }

    public void detachCanonicalRecurrenceEvent(Instant deletedAt) {
        recurrenceEvent = null;
        localDeletedAt = deletedAt;
    }

    public boolean detachCanonicalRecurrenceEventIfAllowed(Instant deletedAt) {
        if (isConflicted() || !integration.isConnected()) {
            detachCanonicalRecurrenceEvent(deletedAt);
            return true;
        }
        return false;
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
