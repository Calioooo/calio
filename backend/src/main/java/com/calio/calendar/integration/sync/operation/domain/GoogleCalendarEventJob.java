package com.calio.calendar.integration.sync.operation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Convert(converter = JsonPayloadConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_payload", updatable = false, columnDefinition = "JSON")
    private String targetPayload;

    protected GoogleCalendarEventJob() {
    }

    public static GoogleCalendarEventJob create(String operationId, Long integrationId, Long accountId,
                                                 long integrationSequence, GoogleCalendarEventJobKind kind,
                                                 Long eventId, String providerIdentity, String targetPayload,
                                                 Instant runnableAt) {
        if (kind == null || eventId == null || !hasText(targetPayload)) {
            throw new IllegalArgumentException("Google Event job fields are required");
        }
        if (kind == GoogleCalendarEventJobKind.CREATE && !hasText(providerIdentity)) {
            throw new IllegalArgumentException("Google Event CREATE job requires provider identity");
        }
        GoogleCalendarEventJob job = new GoogleCalendarEventJob();
        job.initialize(operationId, integrationId, accountId, integrationSequence, runnableAt);
        job.kind = kind;
        job.eventId = eventId;
        job.providerIdentity = providerIdentity;
        job.targetPayload = targetPayload;
        return job;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public GoogleCalendarEventJobKind getKind() { return kind; }
    public Long getEventId() { return eventId; }
    public String getProviderIdentity() { return providerIdentity; }
    public String getTargetPayload() { return targetPayload; }
}
