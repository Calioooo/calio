package com.calio.calendar.integration.sync.operation.domain;

import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "google_operation_jobs")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "job_scope", discriminatorType = DiscriminatorType.STRING, length = 64)
public abstract class GoogleOperationJob extends BaseEntity {

    static final String SYNC_SCOPE = "SYNC";
    static final String EVENT_SCOPE = "GENERAL_EVENT";
    static final String SYNC_KEY = "sync";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_id", nullable = false, updatable = false, length = 36)
    private String operationId;

    @Column(name = "integration_id", nullable = false, updatable = false)
    private Long integrationId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(name = "integration_sequence", nullable = false, updatable = false)
    private long integrationSequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_kind", nullable = false, updatable = false, length = 64)
    private GoogleOperationJobKind kind;

    @Column(name = "effective_resource_scope", nullable = false, updatable = false, length = 64)
    private String effectiveResourceScope;

    @Column(name = "effective_resource_key", nullable = false, updatable = false, length = 1024)
    private String effectiveResourceKey;

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

    protected final void initialize(
            String operationId,
            Long integrationId,
            Long accountId,
            long integrationSequence,
            GoogleOperationJobKind kind,
            String effectiveResourceScope,
            String effectiveResourceKey,
            Instant runnableAt
    ) {
        this.operationId = operationId;
        this.integrationId = integrationId;
        this.accountId = accountId;
        this.integrationSequence = integrationSequence;
        this.kind = kind;
        this.effectiveResourceScope = effectiveResourceScope;
        this.effectiveResourceKey = effectiveResourceKey;
        this.state = GoogleOperationJobState.PENDING;
        this.runnableAt = runnableAt;
    }

    public boolean canBeClaimedAt(Instant now) {
        return !runnableAt.isAfter(now);
    }

    public void claim(String workerToken) {
        state = GoogleOperationJobState.PROCESSING;
        ownerToken = workerToken;
    }

    public Long getId() { return id; }
    public String getOperationId() { return operationId; }
    public Long getIntegrationId() { return integrationId; }
    public Long getAccountId() { return accountId; }
    public long getIntegrationSequence() { return integrationSequence; }
    protected GoogleOperationJobKind getJobKind() { return kind; }
    public GoogleOperationJobState getState() { return state; }
    public Instant getRunnableAt() { return runnableAt; }
    public int getRetryCount() { return retryCount; }
    public String getLastErrorReason() { return lastErrorReason; }
    public String getOwnerToken() { return ownerToken; }
}
