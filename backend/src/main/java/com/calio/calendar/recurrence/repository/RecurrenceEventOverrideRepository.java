package com.calio.calendar.recurrence.repository;

import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurrenceEventOverrideRepository extends JpaRepository<RecurrenceEventOverride, Long> {

    Optional<RecurrenceEventOverride> findByRecurrenceEvent_IdAndOriginStartAt(Long recurrenceId, Instant originStartAt);

    List<RecurrenceEventOverride> findByRecurrenceEvent_IdAndOriginStartAtIn(
            Long recurrenceId,
            Collection<Instant> originStartAt
    );

    @EntityGraph(attributePaths = "recurrenceEvent")
    @Query("""
            select recurrenceOverride
            from RecurrenceEventOverride recurrenceOverride
            where recurrenceOverride.recurrenceEvent.id in :recurrenceIds
              and (
                    (recurrenceOverride.originStartAt >= :from and recurrenceOverride.originStartAt < :to)
                 or (
                        recurrenceOverride.deletedAt is null
                    and recurrenceOverride.overrideStartAt < :to
                    and recurrenceOverride.overrideEndAt > :from
                 )
              )
            """)
    List<RecurrenceEventOverride> findAllForRecurrenceIdsInRange(
            @Param("recurrenceIds") Collection<Long> recurrenceIds,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @EntityGraph(attributePaths = {"recurrenceEvent", "recurrenceEvent.tag"})
    @Query("""
            select recurrenceOverride
            from RecurrenceEventOverride recurrenceOverride
            where recurrenceOverride.recurrenceEvent.account.id = :accountId
              and recurrenceOverride.deletedAt is null
              and recurrenceOverride.overrideStartAt < :to
              and recurrenceOverride.overrideEndAt > :from
            """)
    List<RecurrenceEventOverride> findActiveOverlappingOverrides(
            @Param("accountId") Long accountId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    void deleteAllByRecurrenceEvent_Id(Long recurrenceId);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from RecurrenceEventOverride recurrenceOverride
            where recurrenceOverride.overrideId in :overrideIds
            """)
    int deleteAllByIds(@Param("overrideIds") Collection<Long> overrideIds);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from RecurrenceEventOverride recurrenceOverride
            where recurrenceOverride.recurrenceEvent.id in :recurrenceEventIds
            """)
    int deleteAllByRecurrenceEventIds(
            @Param("recurrenceEventIds") Collection<Long> recurrenceEventIds
    );
}
