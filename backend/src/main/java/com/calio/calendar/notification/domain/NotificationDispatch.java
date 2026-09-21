package com.calio.calendar.notification.domain;

import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

  @Embedded private NotificationScheduleKey scheduleKey;

  @Column(nullable = false)
  private Instant scheduledAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "dispatch_state", nullable = false)
  private NotificationDispatchState state;

  @Column(name = "owner_token", length = 36)
  private String ownerToken;

  @Column(name = "lease_expires_at")
  private Instant leaseExpiresAt;

  @Column(name = "runnable_at")
  private Instant runnableAt;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  protected NotificationDispatch() {}

  private NotificationDispatch(
      Long accountId,
      CalendarNotificationType notificationType,
      NotificationScheduleKey scheduleKey,
      Instant scheduledAt,
      NotificationDispatchState state,
      String ownerToken,
      Instant leaseExpiresAt) {
    this.accountId = Objects.requireNonNull(accountId);
    this.notificationType = Objects.requireNonNull(notificationType);
    this.scheduleKey = Objects.requireNonNull(scheduleKey);
    this.scheduledAt = Objects.requireNonNull(scheduledAt);
    this.state = Objects.requireNonNull(state);
    this.ownerToken = ownerToken;
    this.leaseExpiresAt = leaseExpiresAt;
  }

  public static NotificationDispatch claimed(
      Long accountId,
      CalendarNotificationType notificationType,
      NotificationScheduleKey scheduleKey,
      Instant scheduledAt,
      String ownerToken,
      Instant leaseExpiresAt) {
    Objects.requireNonNull(ownerToken);
    Objects.requireNonNull(leaseExpiresAt);
    return new NotificationDispatch(
        accountId,
        notificationType,
        scheduleKey,
        scheduledAt,
        NotificationDispatchState.PROCESSING,
        ownerToken,
        leaseExpiresAt);
  }

  public static NotificationDispatch completed(
      Long accountId,
      CalendarNotificationType notificationType,
      NotificationScheduleKey scheduleKey,
      Instant scheduledAt) {
    return new NotificationDispatch(
        accountId,
        notificationType,
        scheduleKey,
        scheduledAt,
        NotificationDispatchState.COMPLETED,
        null,
        null);
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

  public NotificationScheduleKey getScheduleKey() {
    return scheduleKey;
  }

  public NotificationDispatchState getState() {
    return state;
  }

  public String getOwnerToken() {
    return ownerToken;
  }

  public Instant getLeaseExpiresAt() {
    return leaseExpiresAt;
  }

  public Instant getRunnableAt() {
    return runnableAt;
  }

  public int getRetryCount() {
    return retryCount;
  }
}
