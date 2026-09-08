package com.calio.calendar.notification.domain;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "notification_deliveries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_deliveries_claim",
                columnNames = {"account_id", "notification_type", "schedule_key", "scheduled_at"}
        )
)
public class NotificationDelivery extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false)
    private String notificationType;

    @Column(nullable = false)
    private String scheduleKey;

    @Column(nullable = false)
    private Instant scheduledAt;

    @Column(nullable = false)
    private LocalDate targetDate;

    private String title;
    private String groupName;

    @Column(nullable = false)
    private String status;

    protected NotificationDelivery() {
    }

    public NotificationDelivery(
            Account account,
            String notificationType,
            String scheduleKey,
            Instant scheduledAt,
            LocalDate targetDate,
            String title,
            String groupName
    ) {
        this.account = account;
        this.notificationType = notificationType;
        this.scheduleKey = scheduleKey;
        this.scheduledAt = scheduledAt;
        this.targetDate = targetDate;
        this.title = title;
        this.groupName = groupName;
        status = "CLAIMED";
    }

    public String getNotificationType() {
        return notificationType;
    }

    public String getTitle() {
        return title;
    }

    public String getGroupName() {
        return groupName;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void complete(String status) {
        this.status = status;
    }
}
