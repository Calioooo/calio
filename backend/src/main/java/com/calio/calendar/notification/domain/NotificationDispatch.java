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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(
        name = "notification_dispatches",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_dispatches_claim",
                columnNames = {"account_id", "notification_type", "schedule_key", "scheduled_at"}
        )
)
public class NotificationDispatch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CalendarNotificationType notificationType;

    @Column(nullable = false)
    private String scheduleKey;

    @Column(nullable = false)
    private Instant scheduledAt;

    @Column(nullable = false)
    private LocalDate targetDate;

    private String title;
    private String groupName;

    protected NotificationDispatch() {
    }

    public NotificationDispatch(
            Account account,
            CalendarNotificationType notificationType,
            NotificationScheduleKey scheduleKey,
            Instant scheduledAt,
            LocalDate targetDate,
            String title,
            String groupName
    ) {
        this.account = Objects.requireNonNull(account);
        this.notificationType = Objects.requireNonNull(notificationType);
        this.scheduleKey = Objects.requireNonNull(scheduleKey).value();
        this.scheduledAt = Objects.requireNonNull(scheduledAt);
        this.targetDate = Objects.requireNonNull(targetDate);
        this.title = title;
        this.groupName = groupName;
    }

    public CalendarNotificationType getNotificationType() {
        return notificationType;
    }

    public Long getId() {
        return id;
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
}
