package com.calio.calendar.integration.domain;

import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

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

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "google_subject", nullable = false)
    private String googleSubject;

    @Column(name = "google_email", nullable = false, length = 320)
    private String googleEmail;

    @Column(name = "encrypted_refresh_token", nullable = false, columnDefinition = "TEXT")
    private String encryptedRefreshToken;

    @Column(name = "encrypted_access_token", nullable = false, columnDefinition = "TEXT")
    private String encryptedAccessToken;

    @Column(name = "access_token_expires_at", nullable = false)
    private Instant accessTokenExpiresAt;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "next_sync_token", columnDefinition = "TEXT")
    private String nextSyncToken;

    @Column(name = "active_sync_run_id", length = 36)
    private String activeSyncRunId;

    @Column(name = "sync_lease_expires_at")
    private Instant syncLeaseExpiresAt;

    @Column(name = "next_google_operation_sequence", nullable = false)
    private long nextGoogleOperationSequence = 1L;

    @Column(name = "google_operation_lease_owner", length = 36)
    private String googleOperationLeaseOwner;

    @Column(name = "google_operation_lease_expires_at")
    private Instant googleOperationLeaseExpiresAt;

    protected GoogleCalendarIntegration() {
    }

    public GoogleCalendarIntegration(
            Long accountId,
            String googleSubject,
            String googleEmail,
            String encryptedRefreshToken,
            String encryptedAccessToken,
            Instant accessTokenExpiresAt,
            Instant connectedAt
    ) {
        this.accountId = accountId;
        replace(
                googleSubject,
                googleEmail,
                encryptedRefreshToken,
                encryptedAccessToken,
                accessTokenExpiresAt,
                connectedAt
        );
    }

    public void replace(
            String googleSubject,
            String googleEmail,
            String encryptedRefreshToken,
            String encryptedAccessToken,
            Instant accessTokenExpiresAt,
            Instant connectedAt
    ) {
        this.googleSubject = googleSubject;
        this.googleEmail = googleEmail;
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.encryptedAccessToken = encryptedAccessToken;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.connectedAt = connectedAt;
        clearSyncState();
    }

    public void clearSyncState() {
        this.nextSyncToken = null;
        this.activeSyncRunId = null;
        this.syncLeaseExpiresAt = null;
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getGoogleSubject() {
        return googleSubject;
    }

    public String getGoogleEmail() {
        return googleEmail;
    }

    public String getEncryptedRefreshToken() {
        return encryptedRefreshToken;
    }

    public String getEncryptedAccessToken() {
        return encryptedAccessToken;
    }

    public Instant getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public String getNextSyncToken() {
        return nextSyncToken;
    }

    public String getActiveSyncRunId() {
        return activeSyncRunId;
    }

    public Instant getSyncLeaseExpiresAt() {
        return syncLeaseExpiresAt;
    }

    public long allocateGoogleOperationSequence() {
        return nextGoogleOperationSequence++;
    }
}
