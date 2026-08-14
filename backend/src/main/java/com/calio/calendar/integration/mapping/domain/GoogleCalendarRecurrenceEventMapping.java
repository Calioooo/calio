package com.calio.calendar.integration.mapping.domain;

import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
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

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recurrence_event_id", nullable = false)
    private RecurrenceEvent recurrenceEvent;

    @Column(name = "calendar_key", nullable = false, length = 32)
    private String calendarKey;

    @Column(name = "external_event_id", nullable = false, length = 1024)
    private String externalEventId;

    @Column(name = "provider_etag", length = 1024)
    private String providerEtag;

    @Column(name = "provider_updated_at")
    private Instant providerUpdatedAt;

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
        this.providerEtag = providerEtag;
        this.providerUpdatedAt = providerUpdatedAt;
    }

    public void updateProviderVersion(String providerEtag, Instant providerUpdatedAt) {
        this.providerEtag = providerEtag;
        this.providerUpdatedAt = providerUpdatedAt;
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

    public String getProviderEtag() {
        return providerEtag;
    }

    public Instant getProviderUpdatedAt() {
        return providerUpdatedAt;
    }
}
