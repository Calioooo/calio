package com.calio.calendar.integration.sync.operation.domain;

import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "google_operation_jobs")
public class GoogleOperationJob extends BaseEntity {

    public static final String SYNC_KIND = "SYNC";
    public static final String PRIMARY_CALENDAR_SCOPE = "PRIMARY_CALENDAR";
    public static final String PRIMARY_CALENDAR_KEY = "primary";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_id", nullable = false, updatable = false, length = 36)
    private String operationId;

    @Column(name = "connection_id", nullable = false, updatable = false)
    private Long connectionId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(name = "account_sequence", nullable = false, updatable = false)
    private long accountSequence;

    @Column(name = "job_kind", nullable = false, updatable = false, length = 64)
    private String kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_trigger", nullable = false, updatable = false, length = 32)
    private GoogleOperationJobTrigger trigger;

    @Column(name = "effective_resource_scope", nullable = false, updatable = false, length = 64)
    private String effectiveResourceScope;

    @Column(name = "effective_resource_key", nullable = false, updatable = false, length = 1024)
    private String effectiveResourceKey;

    @Column(name = "provider_identity", updatable = false, length = 1024)
    private String providerIdentity;

    @Column(name = "target_payload", updatable = false, columnDefinition = "JSON")
    private String targetPayload;

    @Column(name = "conflict_detected", nullable = false)
    private boolean conflictDetected;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_state", nullable = false, length = 32)
    private GoogleOperationJobState state;

    @Column(name = "runnable_at", nullable = false)
    private Instant runnableAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error_reason", length = 128)
    private String lastErrorReason;

    @Column(name = "owner_token", length = 36)
    private String ownerToken;

    @Column(name = "terminal_reason", length = 128)
    private String terminalReason;

    @Column(name = "terminal_at")
    private Instant terminalAt;

    protected GoogleOperationJob() {
    }

    public static GoogleOperationJob sync(
            String operationId,
            Long connectionId,
            Long accountId,
            long accountSequence,
            GoogleOperationJobTrigger trigger,
            Instant runnableAt
    ) {
        validateSyncTrigger(trigger);
        GoogleOperationJob job = new GoogleOperationJob();
        job.operationId = operationId;
        job.connectionId = connectionId;
        job.accountId = accountId;
        job.accountSequence = accountSequence;
        job.kind = SYNC_KIND;
        job.trigger = trigger;
        job.effectiveResourceScope = PRIMARY_CALENDAR_SCOPE;
        job.effectiveResourceKey = PRIMARY_CALENDAR_KEY;
        job.state = GoogleOperationJobState.PENDING;
        job.runnableAt = runnableAt;
        return job;
    }

    public static GoogleOperationJob outbound(
            String operationId,
            Long connectionId,
            Long accountId,
            long accountSequence,
            String kind,
            String resourceScope,
            String resourceKey,
            String providerIdentity,
            String targetPayload,
            Instant runnableAt
    ) {
        validateOutboundFields(kind, resourceScope, resourceKey, targetPayload);
        GoogleOperationJob job = new GoogleOperationJob();
        job.operationId = operationId;
        job.connectionId = connectionId;
        job.accountId = accountId;
        job.accountSequence = accountSequence;
        job.kind = kind;
        job.trigger = GoogleOperationJobTrigger.CANONICAL_MUTATION;
        job.effectiveResourceScope = resourceScope;
        job.effectiveResourceKey = resourceKey;
        job.providerIdentity = providerIdentity;
        job.targetPayload = targetPayload;
        job.state = GoogleOperationJobState.PENDING;
        job.runnableAt = runnableAt;
        return job;
    }

    private static void validateSyncTrigger(GoogleOperationJobTrigger trigger) {
        if (trigger != GoogleOperationJobTrigger.MANUAL
                && trigger != GoogleOperationJobTrigger.PERIODIC) {
            throw new IllegalArgumentException("Sync Google operation trigger must be MANUAL or PERIODIC");
        }
    }

    private static void validateOutboundFields(
            String kind,
            String resourceScope,
            String resourceKey,
            String targetPayload
    ) {
        if (kind == null || kind.isBlank() || SYNC_KIND.equals(kind)
                || resourceScope == null || resourceScope.isBlank()
                || resourceKey == null || resourceKey.isBlank()
                || targetPayload == null || targetPayload.isBlank()) {
            throw new IllegalArgumentException("Outbound Google operation fields are required");
        }
    }

    public boolean canBeClaimedAt(Instant now) {
        return !runnableAt.isAfter(now);
    }

    public void claim(String workerToken) {
        state = GoogleOperationJobState.PROCESSING;
        ownerToken = workerToken;
    }

    public void markConflictDetected() {
        conflictDetected = true;
    }

    public Long getId() { return id; }
    public String getOperationId() { return operationId; }
    public Long getConnectionId() { return connectionId; }
    public Long getAccountId() { return accountId; }
    public long getAccountSequence() { return accountSequence; }
    public String getKind() { return kind; }
    public GoogleOperationJobTrigger getTrigger() { return trigger; }
    public GoogleOperationJobState getState() { return state; }
    public Instant getRunnableAt() { return runnableAt; }
    public int getRetryCount() { return retryCount; }
    public String getLastErrorReason() { return lastErrorReason; }
    public String getOwnerToken() { return ownerToken; }
    public String getEffectiveResourceScope() { return effectiveResourceScope; }
    public String getEffectiveResourceKey() { return effectiveResourceKey; }
    public boolean isConflictDetected() { return conflictDetected; }
}
