package com.calio.calendar.singleevent.repository;

import com.calio.calendar.singleevent.domain.SingleEvent;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SingleEventRepository extends JpaRepository<SingleEvent, Long> {

  Optional<SingleEvent> findByIdAndAccountId(Long id, Long accountId);

  @Query(
      """
            select event
            from SingleEvent event
            where event.id in :eventIds
              and event.accountId = :accountId
            """)
  List<SingleEvent> findAllShareableByIdsAndAccountId(
      @Param("eventIds") List<Long> eventIds, @Param("accountId") Long accountId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
            select event
            from SingleEvent event
            where event.id = :eventId
              and event.accountId = :accountId
            """)
  Optional<SingleEvent> findByIdAndAccountIdForUpdate(
      @Param("eventId") Long eventId, @Param("accountId") Long accountId);

  @Modifying(flushAutomatically = true)
  @Query("delete from SingleEvent event where event.id in :eventIds")
  int deleteAllByIds(@Param("eventIds") List<Long> eventIds);

  @Query(
      """
            select event
            from SingleEvent event
            where event.accountId = :accountId
              and event.schedule.startAt < :to
              and event.schedule.endAt > :from
            order by event.schedule.startAt asc
            """)
  List<SingleEvent> findSingleEvents(
      @Param("accountId") Long accountId, @Param("from") Instant from, @Param("to") Instant to);

  @Modifying(flushAutomatically = true)
  @Query(
      """
            update SingleEvent event
            set event.tagId = :fallbackTagId
            where event.tagId = :sourceTagId and event.accountId = :accountId
            """)
  int reassignAllByTagAndAccountId(
      @Param("sourceTagId") Long sourceTagId,
      @Param("fallbackTagId") Long fallbackTagId,
      @Param("accountId") Long accountId);
}
