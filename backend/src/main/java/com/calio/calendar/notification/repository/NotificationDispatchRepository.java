package com.calio.calendar.notification.repository;

import com.calio.calendar.notification.domain.CalendarNotificationType;
import com.calio.calendar.notification.domain.NotificationDispatch;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDispatchRepository extends JpaRepository<NotificationDispatch, Long> {

  boolean existsByAccountIdAndNotificationTypeAndScheduleKeyAndScheduledAt(
      Long accountId, CalendarNotificationType type, String key, Instant scheduledAt);
}
