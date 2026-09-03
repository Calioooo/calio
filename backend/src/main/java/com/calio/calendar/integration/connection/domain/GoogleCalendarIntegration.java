package com.calio.calendar.integration.connection.domain;

import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    protected GoogleCalendarIntegration() { }
    public GoogleCalendarIntegration(Long accountId) { this.accountId = accountId; }
    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
}
