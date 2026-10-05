package com.calio.calendar.recurrence.repository;

import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Child Entity의 조회 전용 접근. 변경과 저장은 RecurrenceEvent Root를 통해 수행한다. */
public interface RecurrenceEventOverrideRepository
    extends Repository<RecurrenceEventOverride, Long> {

  List<RecurrenceEventOverride> findAll();

  long count();

  Optional<RecurrenceEventOverride> findByRecurrenceEvent_IdAndOriginStartAt(
      Long recurrenceId, Instant originStartAt);

  List<RecurrenceEventOverride> findByRecurrenceEvent_IdAndOriginStartAtIn(
      Long recurrenceId, Collection<Instant> originStartAt);

  @EntityGraph(attributePaths = "recurrenceEvent")
  @Query(
      """
            select recurrenceOverride
            from RecurrenceEventOverride recurrenceOverride
            where recurrenceOverride.recurrenceEvent.accountId = :accountId
              and recurrenceOverride.deletedAt is null
              and recurrenceOverride.overrideStartAt < :to
              and recurrenceOverride.overrideEndAt > :from
            """)
  List<RecurrenceEventOverride> findActiveOverlappingOverrides(
      @Param("accountId") Long accountId, @Param("from") Instant from, @Param("to") Instant to);
}
