package com.calio.calendar.notification.repository;

import com.calio.calendar.notification.domain.CalendarNotificationType;
import com.calio.calendar.notification.domain.NotificationDispatch;
import com.calio.calendar.notification.domain.NotificationScheduleKey;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationDispatchRepository extends JpaRepository<NotificationDispatch, Long> {

  @Query(
      """
      select case when count(dispatch) > 0 then true else false end
      from NotificationDispatch dispatch
      where dispatch.accountId = :accountId
        and dispatch.notificationType = :notificationType
        and dispatch.scheduleKey = :scheduleKey
        and dispatch.scheduledAt = :scheduledAt
      """)
  boolean hasDispatchClaim(
      @Param("accountId") Long accountId,
      @Param("notificationType") CalendarNotificationType notificationType,
      @Param("scheduleKey") NotificationScheduleKey scheduleKey,
      @Param("scheduledAt") Instant scheduledAt);
}
