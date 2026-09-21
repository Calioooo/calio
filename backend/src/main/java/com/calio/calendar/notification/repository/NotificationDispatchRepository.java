package com.calio.calendar.notification.repository;

import com.calio.calendar.notification.domain.CalendarNotificationType;
import com.calio.calendar.notification.domain.NotificationDispatch;
import com.calio.calendar.notification.domain.NotificationDispatchState;
import com.calio.calendar.notification.domain.NotificationScheduleKey;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationDispatchRepository extends JpaRepository<NotificationDispatch, Long> {

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from NotificationDispatch dispatch where dispatch.scheduledAt < :cutoff")
  int deleteScheduledBefore(@Param("cutoff") Instant cutoff);

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

  @Query(
      """
      select dispatch
      from NotificationDispatch dispatch
      where dispatch.accountId = :accountId
        and dispatch.notificationType = :notificationType
        and dispatch.scheduleKey = :scheduleKey
        and dispatch.scheduledAt = :scheduledAt
      """)
  Optional<NotificationDispatch> findDispatchClaim(
      @Param("accountId") Long accountId,
      @Param("notificationType") CalendarNotificationType notificationType,
      @Param("scheduleKey") NotificationScheduleKey scheduleKey,
      @Param("scheduledAt") Instant scheduledAt);

  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update NotificationDispatch dispatch
      set dispatch.state = :processingState,
          dispatch.ownerToken = :ownerToken,
          dispatch.leaseExpiresAt = :leaseExpiresAt,
          dispatch.runnableAt = null
      where dispatch.id = :dispatchId
        and (
          (dispatch.state = :retryableState and dispatch.runnableAt <= :now)
          or (dispatch.state = :processingState and dispatch.leaseExpiresAt <= :now)
        )
      """)
  int tryAcquire(
      @Param("dispatchId") Long dispatchId,
      @Param("ownerToken") String ownerToken,
      @Param("now") Instant now,
      @Param("leaseExpiresAt") Instant leaseExpiresAt,
      @Param("processingState") NotificationDispatchState processingState,
      @Param("retryableState") NotificationDispatchState retryableState);

  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update NotificationDispatch dispatch
      set dispatch.state = :retryableState,
          dispatch.ownerToken = null,
          dispatch.leaseExpiresAt = null,
          dispatch.runnableAt = :runnableAt,
          dispatch.retryCount = dispatch.retryCount + 1
      where dispatch.id = :dispatchId
        and dispatch.state = :processingState
        and dispatch.ownerToken = :ownerToken
      """)
  int markRetryable(
      @Param("dispatchId") Long dispatchId,
      @Param("ownerToken") String ownerToken,
      @Param("runnableAt") Instant runnableAt,
      @Param("processingState") NotificationDispatchState processingState,
      @Param("retryableState") NotificationDispatchState retryableState);

  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update NotificationDispatch dispatch
      set dispatch.state = :completedState,
          dispatch.ownerToken = null,
          dispatch.leaseExpiresAt = null,
          dispatch.runnableAt = null
      where dispatch.id = :dispatchId
        and dispatch.state = :processingState
        and dispatch.ownerToken = :ownerToken
      """)
  int markCompleted(
      @Param("dispatchId") Long dispatchId,
      @Param("ownerToken") String ownerToken,
      @Param("processingState") NotificationDispatchState processingState,
      @Param("completedState") NotificationDispatchState completedState);
}
