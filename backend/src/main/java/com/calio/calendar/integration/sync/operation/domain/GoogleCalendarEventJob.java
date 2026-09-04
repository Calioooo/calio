package com.calio.calendar.integration.sync.operation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.time.Instant;

@Entity
@DiscriminatorValue("EVENT")
public class GoogleCalendarEventJob extends GoogleOperationJob {

    @Column(name = "event_id", updatable = false)
    private Long eventId;

    @Column(name = "provider_identity", updatable = false, length = 1024)
    private String providerIdentity;

    @Column(name = "target_payload", updatable = false, columnDefinition = "JSON")
    private String targetPayload;

    protected GoogleCalendarEventJob() {
    }

    public static GoogleCalendarEventJob create(String operationId, Long integrationId, Long accountId,
                                                 long integrationSequence, GoogleOperationJobKind kind,
                                                 Long eventId, String providerIdentity, String targetPayload,
                                                 Instant runnableAt) {
        if ((kind != GoogleOperationJobKind.CREATE && kind != GoogleOperationJobKind.UPDATE
                && kind != GoogleOperationJobKind.DELETE) || eventId == null
                || (kind == GoogleOperationJobKind.CREATE
                && (providerIdentity == null || providerIdentity.isBlank()))
                || targetPayload == null || targetPayload.isBlank()) {
            throw new IllegalArgumentException("Google Event job fields are required");
        }
        GoogleCalendarEventJob job = new GoogleCalendarEventJob();
        job.initialize(operationId, integrationId, accountId, integrationSequence,
                kind, EVENT_SCOPE, eventId.toString(), runnableAt);
        job.eventId = eventId;
        job.providerIdentity = providerIdentity;
        job.targetPayload = targetPayload;
        return job;
    }

    public GoogleOperationJobKind getKind() { return getJobKind(); }
    public Long getEventId() { return eventId; }
    public String getProviderIdentity() { return providerIdentity; }
    public String getTargetPayload() { return targetPayload; }
}
