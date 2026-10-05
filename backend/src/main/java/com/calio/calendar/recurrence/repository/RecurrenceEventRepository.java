package com.calio.calendar.recurrence.repository;

import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.repository.dto.RecurrenceOverrideView;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurrenceEventRepository extends JpaRepository<RecurrenceEvent, Long> {

  Optional<RecurrenceEvent> findByIdAndAccountId(Long id, Long accountId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select event from RecurrenceEvent event where event.id = :recurrenceId")
  Optional<RecurrenceEvent> findByIdForUpdate(@Param("recurrenceId") Long recurrenceId);

  @Query(
      """
            select recurrenceEvent
            from RecurrenceEvent recurrenceEvent
            where recurrenceEvent.accountId = :accountId
              and recurrenceEvent.schedule.firstOccurrenceStartAt < :to
            """)
  List<RecurrenceEvent> findExpansionCandidatesStartedBefore(
      @Param("accountId") Long accountId, @Param("to") Instant to);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
            select recurrenceEvent
            from RecurrenceEvent recurrenceEvent
            where recurrenceEvent.id = :recurrenceId
              and recurrenceEvent.accountId = :accountId
            """)
  Optional<RecurrenceEvent> findByIdAndAccountIdForUpdate(
      @Param("recurrenceId") Long recurrenceId, @Param("accountId") Long accountId);

  @Modifying(flushAutomatically = true)
  @Query(
      """
            update RecurrenceEvent recurrenceEvent
            set recurrenceEvent.tagId = :fallbackTagId
            where recurrenceEvent.tagId = :sourceTagId and recurrenceEvent.accountId = :accountId
            """)
  int reassignAllByTagAndAccountId(
      @Param("sourceTagId") Long sourceTagId,
      @Param("fallbackTagId") Long fallbackTagId,
      @Param("accountId") Long accountId);

  @Query(
      "select override from RecurrenceEvent event join event.overrides override where event.id = :recurrenceId and override.originStartAt = :originStartAt")
  Optional<RecurrenceEventOverride> findOverrideByRecurrenceIdAndOriginStartAt(
      @Param("recurrenceId") Long recurrenceId, @Param("originStartAt") Instant originStartAt);

  @Query(
      "select override from RecurrenceEvent event join event.overrides override where event.id = :recurrenceId and override.originStartAt in :origins")
  List<RecurrenceEventOverride> findOverridesByRecurrenceIdAndOriginStartAtIn(
      @Param("recurrenceId") Long recurrenceId, @Param("origins") Collection<Instant> origins);

  @Query(
      """
      select new com.calio.calendar.recurrence.repository.dto.RecurrenceOverrideView(event, override)
      from RecurrenceEvent event join event.overrides override
      where event.accountId = :accountId and override.deletedAt is null
        and override.schedule.startAt < :to and override.schedule.endAt > :from
      """)
  List<RecurrenceOverrideView> findActiveOverlappingOverrides(
      @Param("accountId") Long accountId, @Param("from") Instant from, @Param("to") Instant to);

  @Query("select override from RecurrenceEvent event join event.overrides override")
  List<RecurrenceEventOverride> findAllOverrides();

  @Query(
      "select count(override.originStartAt) from RecurrenceEvent event join event.overrides override")
  long countOverrides();
}
