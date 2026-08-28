package com.calio.calendar.integration.connection.domain;

import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "google_calendar_integrations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_google_calendar_integration_account_id",
                columnNames = "account_id"
        )
)
public class GoogleCalendarIntegration extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(name = "next_google_operation_sequence", nullable = false)
    private long nextGoogleOperationSequence = 1L;

    @Column(name = "google_operation_lease_owner", length = 36)
    private String googleOperationLeaseOwner;

    @Column(name = "google_operation_lease_expires_at")
    private Instant googleOperationLeaseExpiresAt;

    protected GoogleCalendarIntegration() { }
    public GoogleCalendarIntegration(Long accountId) { this.accountId = accountId; }

    public long allocateGoogleOperationSequence() {
        return nextGoogleOperationSequence++;
    }
    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
}
