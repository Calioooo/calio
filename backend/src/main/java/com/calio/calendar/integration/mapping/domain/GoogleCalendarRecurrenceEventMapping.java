package com.calio.calendar.integration.mapping.domain;

import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "google_calendar_recurrence_event_mappings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_google_calendar_recurrence_event_external",
                        columnNames = {"connection_id", "calendar_key", "external_event_id"}
                ),
                @UniqueConstraint(
                        name = "uk_google_calendar_recurrence_event_connection_canonical",
                        columnNames = {"connection_id", "recurrence_event_id"}
                )
        }
)
public class GoogleCalendarRecurrenceEventMapping extends BaseEntity {

    public static final String PRIMARY_CALENDAR_KEY = "primary";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id", nullable = false)
    private GoogleCalendarConnection connection;

    @Column(name = "recurrence_event_id", nullable = false, updatable = false)
    private Long recurrenceEventId;

    @Column(name = "calendar_key", nullable = false, length = 32)
    private String calendarKey;

    @Column(name = "external_event_id", nullable = false, length = 1024)
    private String externalEventId;

    @Embedded
    private GoogleCalendarMappingSyncState syncState;

    @Column(name = "local_changed", nullable = false)
    private boolean localChanged;

    protected GoogleCalendarRecurrenceEventMapping() {
    }

    public GoogleCalendarRecurrenceEventMapping(
            GoogleCalendarConnection connection,
            Long recurrenceEventId,
            String externalEventId,
            String providerEtag
    ) {
        this.connection = connection;
        this.recurrenceEventId = recurrenceEventId;
        this.calendarKey = PRIMARY_CALENDAR_KEY;
        this.externalEventId = externalEventId;
        this.syncState = GoogleCalendarMappingSyncState.active(providerEtag);
    }

    public Long getId() {
        return id;
    }

    public GoogleCalendarConnection getConnection() {
        return connection;
    }

    public Long getRecurrenceEventId() {
        return recurrenceEventId;
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

    public GoogleCalendarRecurrenceEventMapping(
            GoogleCalendarConnection connection,
            RecurrenceEvent recurrenceEvent,
            String externalEventId,
            String providerEtag
    ) {
        this(connection, recurrenceEvent.getId(), externalEventId, providerEtag);
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
