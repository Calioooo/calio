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
@DiscriminatorValue("RECURRENCE")
public class GoogleCalendarRecurrenceJob extends GoogleOperationJob {

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_operation_kind", updatable = false, length = 64)
    private GoogleCalendarRecurrenceJobKind kind;

    @Column(name = "recurrence_event_id", updatable = false)
    private Long recurrenceEventId;

    @Column(name = "origin_start_at", updatable = false)
    private Instant originStartAt;

    @Convert(converter = JsonPayloadConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recurrence_target_payload", updatable = false, columnDefinition = "JSON")
    private String targetPayload;

    @Column(name = "recurrence_provider_identity", updatable = false, length = 1024)
    private String providerIdentity;

    protected GoogleCalendarRecurrenceJob() { }

    public static GoogleCalendarRecurrenceJob create(
            String operationId, Long integrationId, Long accountId, long integrationSequence,
            GoogleCalendarRecurrenceJobKind kind, Long recurrenceEventId, Instant originStartAt,
            String targetPayload, String providerIdentity, Instant runnableAt
    ) {
        if (kind == null || recurrenceEventId == null || targetPayload == null || targetPayload.isBlank()) {
            throw new IllegalArgumentException("Google recurrence job fields are required");
        }
        if (kind.isOverrideJob() && originStartAt == null) {
            throw new IllegalArgumentException("Google recurrence override job requires originStartAt");
        }
        if ((kind == GoogleCalendarRecurrenceJobKind.RECURRENCE_CREATE) && !hasText(providerIdentity)) {
            throw new IllegalArgumentException("Only Google recurrence master create requires provider identity");
        }
        GoogleCalendarRecurrenceJob job = new GoogleCalendarRecurrenceJob();
        job.initialize(operationId, integrationId, accountId, integrationSequence, runnableAt);
        job.kind = kind;
        job.recurrenceEventId = recurrenceEventId;
        job.originStartAt = originStartAt;
        job.targetPayload = targetPayload;
        job.providerIdentity = providerIdentity;
        return job;
    }

    public GoogleCalendarRecurrenceJobKind getKind() { return kind; }
    public Long getRecurrenceEventId() { return recurrenceEventId; }
    public Instant getOriginStartAt() { return originStartAt; }
    public String getTargetPayload() { return targetPayload; }
    public String getProviderIdentity() { return providerIdentity; }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
