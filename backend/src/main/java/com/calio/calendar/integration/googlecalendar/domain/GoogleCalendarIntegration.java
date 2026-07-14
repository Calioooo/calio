package com.calio.calendar.integration.googlecalendar.domain;

import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
    private Long googleCalendarIntegrationId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "google_subject", nullable = false)
    private String googleSubject;

    @Column(name = "google_email", nullable = false)
    private String googleEmail;

    @Column(name = "encrypted_refresh_token", nullable = false, length = 2048)
    private String encryptedRefreshToken;

    @Column(name = "access_token", nullable = false, length = 2048)
    private String accessToken;

    @Column(name = "access_token_expires_at", nullable = false)
    private Instant accessTokenExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false)
    private ConnectionStatus connectionStatus;

    @Column(name = "connected_at")
    private Instant connectedAt;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    protected GoogleCalendarIntegration() {
    }

    public GoogleCalendarIntegration(
            Long accountId,
            String googleSubject,
            String googleEmail,
            String encryptedRefreshToken,
            String accessToken,
            Instant accessTokenExpiresAt,
            Instant connectedAt
    ) {
        this.accountId = accountId;
        connect(googleSubject, googleEmail, encryptedRefreshToken, accessToken, accessTokenExpiresAt, connectedAt);
    }

    public void connect(
            String googleSubject,
            String googleEmail,
            String encryptedRefreshToken,
            String accessToken,
            Instant accessTokenExpiresAt,
            Instant connectedAt
    ) {
        this.googleSubject = googleSubject;
        this.googleEmail = googleEmail;
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.accessToken = accessToken;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.connectionStatus = ConnectionStatus.CONNECTED;
        this.connectedAt = connectedAt;
        this.disconnectedAt = null;
    }

    public boolean isConnected() {
        return connectionStatus == ConnectionStatus.CONNECTED;
    }

    public Long getGoogleCalendarIntegrationId() {
        return googleCalendarIntegrationId;
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

    public String getAccessToken() {
        return accessToken;
    }

    public Instant getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public Instant getDisconnectedAt() {
        return disconnectedAt;
    }
}
