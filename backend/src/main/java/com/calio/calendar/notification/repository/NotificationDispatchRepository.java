package com.calio.calendar.notification.repository;

import com.calio.calendar.notification.domain.NotificationDispatch;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDispatchRepository extends JpaRepository<NotificationDispatch, Long> {

    Optional<NotificationDispatch> findByAccount_IdAndNotificationTypeAndScheduleKeyAndScheduledAt(
            Long accountId,
            String type,
            String key,
            Instant scheduledAt
    );
}
