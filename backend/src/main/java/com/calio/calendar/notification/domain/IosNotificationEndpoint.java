package com.calio.calendar.notification.domain;

import com.calio.calendar.account.domain.Account;
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
@Table(name = "ios_notification_endpoints")
public class IosNotificationEndpoint extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "installation_id", nullable = false)
    private String installationId;

    @Column(name = "apns_token", nullable = false)
    private String apnsToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "authorization_status", nullable = false)
    private IosNotificationAuthorizationStatus authorizationStatus;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private String environment;

    private Instant deactivatedAt;

    protected IosNotificationEndpoint() {
    }

    public IosNotificationEndpoint(
            Account account,
            String installationId,
            String apnsToken,
            IosNotificationAuthorizationStatus authorizationStatus,
            String environment
    ) {
        this.account = account;
        this.installationId = installationId;
        refresh(apnsToken, authorizationStatus, environment);
    }

    public void refresh(
            String apnsToken,
            IosNotificationAuthorizationStatus authorizationStatus,
            String environment
    ) {
        this.apnsToken = apnsToken;
        this.authorizationStatus = authorizationStatus;
        this.environment = environment;
        active = true;
        deactivatedAt = null;
    }

    public void deactivate(Instant now) {
        active = false;
        deactivatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getApnsToken() {
        return apnsToken;
    }
}
