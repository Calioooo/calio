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
public class GoogleCalendarEventMapping extends BaseEntity
        implements GoogleCalendarMappingSyncStateOwner {

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

    @Embedded
    private GoogleCalendarMappingSyncState syncState;

    protected GoogleCalendarEventMapping() {
    }

    public GoogleCalendarEventMapping(
            GoogleCalendarIntegration integration,
            Event event,
            String externalEventId,
            GoogleProviderObservation observation
    ) {
        this.integration = integration;
        this.event = event;
        this.calendarKey = PRIMARY_CALENDAR_KEY;
        this.externalEventId = externalEventId;
        this.syncState = GoogleCalendarMappingSyncState.active(observation);
    }

    public GoogleCalendarEventMapping(
            GoogleCalendarIntegration integration,
            Event event,
            String externalEventId,
            String providerEtag,
            Instant providerUpdatedAt
    ) {
        this(integration, event, externalEventId,
                new GoogleProviderObservation(providerEtag, providerUpdatedAt,
                        GoogleCalendarContentHasher.hash(event)));
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

    @Override
    public GoogleCalendarMappingSyncState getSyncState() {
        return syncState;
    }
}
