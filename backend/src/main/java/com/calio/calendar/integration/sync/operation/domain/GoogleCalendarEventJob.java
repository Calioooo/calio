package com.calio.calendar.integration.sync.operation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;

@Entity
@DiscriminatorValue("EVENT")
public class GoogleCalendarEventJob extends GoogleOperationJob {

    @Enumerated(EnumType.STRING)
    @Column(name = "event_operation_kind", updatable = false, length = 64)
    private GoogleCalendarEventJobKind kind;

    @Column(name = "event_id", updatable = false)
    private Long eventId;

    @Column(name = "provider_identity", updatable = false, length = 1024)
    private String providerIdentity;

    @Column(name = "target_payload", updatable = false, columnDefinition = "JSON")
    private String targetPayload;

    protected GoogleCalendarEventJob() {
    }

    public static GoogleCalendarEventJob create(String operationId, Long integrationId, Long accountId,
                                                 long integrationSequence, GoogleCalendarEventJobKind kind,
                                                 Long eventId, String providerIdentity, String targetPayload,
                                                 Instant runnableAt) {
        if ((kind != GoogleCalendarEventJobKind.CREATE && kind != GoogleCalendarEventJobKind.UPDATE
                && kind != GoogleCalendarEventJobKind.DELETE) || eventId == null
                || (kind == GoogleCalendarEventJobKind.CREATE
                && (providerIdentity == null || providerIdentity.isBlank()))
                || targetPayload == null || targetPayload.isBlank()) {
            throw new IllegalArgumentException("Google Event job fields are required");
        }
        GoogleCalendarEventJob job = new GoogleCalendarEventJob();
        job.initialize(operationId, integrationId, accountId, integrationSequence, runnableAt);
        job.kind = kind;
        job.eventId = eventId;
        job.providerIdentity = providerIdentity;
        job.targetPayload = targetPayload;
        return job;
    }

    public GoogleCalendarEventJobKind getKind() { return kind; }
    public Long getEventId() { return eventId; }
    public String getProviderIdentity() { return providerIdentity; }
    public String getTargetPayload() { return targetPayload; }
}
