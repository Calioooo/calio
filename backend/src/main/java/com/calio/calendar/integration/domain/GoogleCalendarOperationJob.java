package com.calio.calendar.integration.domain;

import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "google_calendar_operation_jobs")
public class GoogleCalendarOperationJob extends BaseEntity {

    public static final String PERIODIC_SYNC_DEDUP_KEY = "PERIODIC_SYNC";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_id", nullable = false, updatable = false, length = 36)
    private String operationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "integration_id", nullable = false, updatable = false)
    private GoogleCalendarIntegration integration;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private long sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_kind", nullable = false, updatable = false, length = 48)
    private GoogleCalendarOperationKind operationKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, updatable = false, length = 32)
    private GoogleCalendarOperationTrigger triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, updatable = false, length = 48)
    private GoogleCalendarOperationScope scopeType;

    @Column(name = "scope_key", updatable = false, length = 1024)
    private String scopeKey;

    @Column(name = "desired_payload", updatable = false, columnDefinition = "TEXT")
    private String desiredPayload;

    @Column(name = "provider_identity", updatable = false, length = 1024)
    private String providerIdentity;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_status", nullable = false, length = 32)
    private GoogleCalendarOperationStatus status;

    @Column(name = "runnable_at", nullable = false)
    private Instant runnableAt;

    @Column(name = "retry_tier", nullable = false)
    private int retryTier;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "owner_token", length = 36)
    private String ownerToken;

    @Column(name = "periodic_dedup_key", length = 32)
    private String periodicDedupKey;

    @Column(name = "terminal_reason", length = 1024)
    private String terminalReason;

    @Column(name = "terminal_at")
    private Instant terminalAt;

    protected GoogleCalendarOperationJob() {
    }

    public static GoogleCalendarOperationJob sync(
            GoogleCalendarIntegration integration,
            long sequenceNumber,
            GoogleCalendarOperationTrigger trigger,
            Instant runnableAt
    ) {
        return new GoogleCalendarOperationJob(
                integration,
                sequenceNumber,
                GoogleCalendarOperationKind.SYNC,
                trigger,
                GoogleCalendarOperationScope.INTEGRATION,
                null,
                null,
                null,
                runnableAt
        );
    }

    public GoogleCalendarOperationJob(
            GoogleCalendarIntegration integration,
            long sequenceNumber,
            GoogleCalendarOperationKind operationKind,
            GoogleCalendarOperationTrigger triggerType,
            GoogleCalendarOperationScope scopeType,
            String scopeKey,
            String desiredPayload,
            String providerIdentity,
            Instant runnableAt
    ) {
        this.operationId = UUID.randomUUID().toString();
        this.integration = Objects.requireNonNull(integration);
        this.accountId = integration.getAccountId();
        this.sequenceNumber = sequenceNumber;
        this.operationKind = Objects.requireNonNull(operationKind);
        this.triggerType = Objects.requireNonNull(triggerType);
        this.scopeType = Objects.requireNonNull(scopeType);
        this.scopeKey = scopeKey;
        this.desiredPayload = desiredPayload;
        this.providerIdentity = providerIdentity;
        this.status = GoogleCalendarOperationStatus.PENDING;
        this.runnableAt = Objects.requireNonNull(runnableAt);
        this.periodicDedupKey = triggerType == GoogleCalendarOperationTrigger.PERIODIC
                ? PERIODIC_SYNC_DEDUP_KEY
                : null;
    }

    public void claim(String ownerToken) {
        if (status != GoogleCalendarOperationStatus.PENDING) {
            throw new IllegalStateException("Only pending operations can be claimed");
        }
        this.status = GoogleCalendarOperationStatus.PROCESSING;
        this.ownerToken = Objects.requireNonNull(ownerToken);
        this.attemptCount++;
    }

    public void retryAt(Instant nextRunnableAt, int nextRetryTier) {
        this.status = GoogleCalendarOperationStatus.PENDING;
        this.ownerToken = null;
        this.runnableAt = Objects.requireNonNull(nextRunnableAt);
        this.retryTier = nextRetryTier;
    }

    public void terminate(
            GoogleCalendarOperationStatus terminalStatus,
            String reason,
            Instant terminalAt
    ) {
        if (!terminalStatus.isTerminal()) {
            throw new IllegalArgumentException("Operation status must be terminal");
        }
        this.status = terminalStatus;
        this.ownerToken = null;
        this.periodicDedupKey = null;
        this.terminalReason = reason;
        this.terminalAt = Objects.requireNonNull(terminalAt);
    }

    public Long getId() { return id; }
    public String getOperationId() { return operationId; }
    public GoogleCalendarIntegration getIntegration() { return integration; }
    public Long getAccountId() { return accountId; }
    public long getSequenceNumber() { return sequenceNumber; }
    public GoogleCalendarOperationKind getOperationKind() { return operationKind; }
    public GoogleCalendarOperationTrigger getTriggerType() { return triggerType; }
    public GoogleCalendarOperationScope getScopeType() { return scopeType; }
    public String getScopeKey() { return scopeKey; }
    public String getDesiredPayload() { return desiredPayload; }
    public String getProviderIdentity() { return providerIdentity; }
    public GoogleCalendarOperationStatus getStatus() { return status; }
    public Instant getRunnableAt() { return runnableAt; }
    public int getRetryTier() { return retryTier; }
    public int getAttemptCount() { return attemptCount; }
    public String getOwnerToken() { return ownerToken; }
    public String getTerminalReason() { return terminalReason; }
    public Instant getTerminalAt() { return terminalAt; }
}
