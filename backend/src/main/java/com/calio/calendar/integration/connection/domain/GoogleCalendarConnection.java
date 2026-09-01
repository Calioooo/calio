package com.calio.calendar.integration.connection.domain;

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

@Entity
@Table(name = "google_calendar_connections")
public class GoogleCalendarConnection extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "integration_id", nullable = false)
    private GoogleCalendarIntegration integration;

    @Column(name = "google_subject", nullable = false, updatable = false)
    private String googleSubject;

    @Column(name = "google_email", nullable = false, length = 320)
    private String googleEmail;

    @Column(name = "encrypted_refresh_token", columnDefinition = "TEXT")
    private String encryptedRefreshToken;

    @Column(name = "encrypted_access_token", columnDefinition = "TEXT")
    private String encryptedAccessToken;

    @Column(name = "access_token_expires_at")
    private Instant accessTokenExpiresAt;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "next_sync_token", columnDefinition = "TEXT")
    private String nextSyncToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_state", nullable = false, length = 32)
    private GoogleCalendarConnectionState state = GoogleCalendarConnectionState.CONNECTED;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    @Column(name = "sync_error_reason", length = 128)
    private String syncErrorReason;

    @Column(name = "sync_error_at")
    private Instant syncErrorAt;

    protected GoogleCalendarConnection() {
    }

    public GoogleCalendarConnection(
            GoogleCalendarIntegration integration,
            String googleSubject,
            String googleEmail,
            String encryptedRefreshToken,
            String encryptedAccessToken,
            Instant accessTokenExpiresAt,
            Instant connectedAt
    ) {
        this.integration = integration;
        this.googleSubject = googleSubject;
        replaceCredentials(googleEmail, encryptedRefreshToken, encryptedAccessToken, accessTokenExpiresAt, connectedAt);
    }
    public void replaceCredentials(
            String googleEmail,
            String encryptedRefreshToken,
            String encryptedAccessToken,
            Instant accessTokenExpiresAt,
            Instant connectedAt
    ) {
        this.googleEmail = googleEmail;
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.encryptedAccessToken = encryptedAccessToken;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.connectedAt = connectedAt;
        state = GoogleCalendarConnectionState.CONNECTED;
        disconnectedAt = null;
        syncErrorReason = null;
        syncErrorAt = null;
        nextSyncToken = null;
    }

    public void disconnect(Instant at) {
        encryptedRefreshToken = null;
        encryptedAccessToken = null;
        accessTokenExpiresAt = null;
        nextSyncToken = null;
        state = GoogleCalendarConnectionState.DISCONNECTED;
        disconnectedAt = at;
        syncErrorReason = null;
        syncErrorAt = null;
    }

    public void markSyncError(String reason, Instant at) {
        state = GoogleCalendarConnectionState.SYNC_ERROR;
        disconnectedAt = null;
        syncErrorReason = reason;
        syncErrorAt = at;
    }

    public void replaceAccessToken(String encryptedAccessToken, Instant accessTokenExpiresAt) {
        this.encryptedAccessToken = encryptedAccessToken;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
    }

    public void replaceNextSyncToken(String nextSyncToken) {
        this.nextSyncToken = nextSyncToken;
    }

    public boolean isConnected() {
        return state == GoogleCalendarConnectionState.CONNECTED;
    }

    public Long getId() {
        return id;
    }

    public GoogleCalendarIntegration getIntegration() {
        return integration;
    }

    public Long getAccountId() {
        return integration.getAccountId();
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

    public GoogleCalendarConnectionState getState() {
        return state;
    }

    public Instant getDisconnectedAt() {
        return disconnectedAt;
    }

    public String getSyncErrorReason() {
        return syncErrorReason;
    }

    public Instant getSyncErrorAt() {
        return syncErrorAt;
    }

    public String getNextSyncToken() {
        return nextSyncToken;
    }
}
