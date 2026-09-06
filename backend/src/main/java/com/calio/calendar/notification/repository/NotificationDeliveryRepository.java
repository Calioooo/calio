package com.calio.calendar.notification.repository;
import com.calio.calendar.notification.domain.NotificationDelivery;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery,Long>{ Optional<NotificationDelivery> findByAccount_IdAndNotificationTypeAndScheduleKeyAndScheduledAt(Long accountId,String type,String key,Instant scheduledAt); }
