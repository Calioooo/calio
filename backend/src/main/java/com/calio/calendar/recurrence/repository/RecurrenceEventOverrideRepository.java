package com.calio.calendar.recurrence.repository;

import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurrenceEventOverrideRepository extends JpaRepository<RecurrenceEventOverride, Long> {

    Optional<RecurrenceEventOverride> findByRecurrenceEvent_IdAndOriginStartAt(Long recurrenceId, Instant originStartAt);

    List<RecurrenceEventOverride> findByRecurrenceEvent_IdAndOriginStartAtIn(
            Long recurrenceId,
            Collection<Instant> originStartAt
    );

    @Query("""
            select recurrenceOverride
            from RecurrenceEventOverride recurrenceOverride
            join fetch recurrenceOverride.recurrenceEvent recurrenceEvent
            join fetch recurrenceEvent.tag
            where recurrenceEvent.account.id = :accountId
              and recurrenceOverride.deletedAt is null
              and recurrenceOverride.overrideStartAt < :to
              and recurrenceOverride.overrideEndAt > :from
            """)
    List<RecurrenceEventOverride> findModifiedOverlappingOverrides(
            @Param("accountId") Long accountId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    void deleteByRecurrenceEvent_Id(Long recurrenceId);
}
