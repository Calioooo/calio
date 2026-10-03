package com.calio.calendar.recurrence.repository;

import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurrenceEventRepository extends JpaRepository<RecurrenceEvent, Long> {

  Optional<RecurrenceEvent> findByIdAndAccountId(Long id, Long accountId);

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
}
