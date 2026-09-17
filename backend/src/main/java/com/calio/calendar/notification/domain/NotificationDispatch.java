package com.calio.calendar.notification.domain;

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
import java.util.Objects;

@Entity
@Table(
    name = "notification_dispatches",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_notification_dispatches_claim",
            columnNames = {"account_id", "notification_type", "schedule_key", "scheduled_at"}))
public class NotificationDispatch extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long accountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CalendarNotificationType notificationType;

  @Column(nullable = false)
  private String scheduleKey;

  @Column(nullable = false)
  private Instant scheduledAt;

  protected NotificationDispatch() {}

  public NotificationDispatch(
      Long accountId,
      CalendarNotificationType notificationType,
      NotificationScheduleKey scheduleKey,
      Instant scheduledAt) {
    this.accountId = Objects.requireNonNull(accountId);
    this.notificationType = Objects.requireNonNull(notificationType);
    this.scheduleKey = Objects.requireNonNull(scheduleKey).value();
    this.scheduledAt = Objects.requireNonNull(scheduledAt);
  }

  public CalendarNotificationType getNotificationType() {
    return notificationType;
  }

  public Long getId() {
    return id;
  }

  public Instant getScheduledAt() {
    return scheduledAt;
  }
}
