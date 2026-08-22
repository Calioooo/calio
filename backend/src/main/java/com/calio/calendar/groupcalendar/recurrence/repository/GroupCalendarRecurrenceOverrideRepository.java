package com.calio.calendar.groupcalendar.recurrence.repository;

import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceOverride;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupCalendarRecurrenceOverrideRepository
        extends JpaRepository<GroupCalendarRecurrenceOverride, Long> {

    @EntityGraph(attributePaths = {"recurrenceEvent", "recurrenceEvent.tag"})
    @Query("""
            select recurrenceOverride
            from GroupCalendarRecurrenceOverride recurrenceOverride
            where recurrenceOverride.recurrenceEvent.id = :recurrenceId
              and recurrenceOverride.originStartAt = :originStartAt
            """)
    Optional<GroupCalendarRecurrenceOverride> findByRecurrenceEvent_IdAndOriginStartAt(
            @Param("recurrenceId") Long recurrenceId,
            @Param("originStartAt") Instant originStartAt
    );

    @EntityGraph(attributePaths = {"recurrenceEvent", "recurrenceEvent.tag"})
    @Query("""
            select recurrenceOverride
            from GroupCalendarRecurrenceOverride recurrenceOverride
            where recurrenceOverride.recurrenceEvent.id = :recurrenceId
              and recurrenceOverride.originStartAt in :originStartAts
            """)
    List<GroupCalendarRecurrenceOverride> findByRecurrenceEvent_IdAndOriginStartAtIn(
            @Param("recurrenceId") Long recurrenceId,
            @Param("originStartAts") Collection<Instant> originStartAts
    );

    @EntityGraph(attributePaths = {"recurrenceEvent", "recurrenceEvent.tag"})
    @Query("""
            select recurrenceOverride
            from GroupCalendarRecurrenceOverride recurrenceOverride
            where recurrenceOverride.recurrenceEvent.groupSpace.id = :groupSpaceId
              and recurrenceOverride.deletedAt is null
              and recurrenceOverride.startAt < :to
              and recurrenceOverride.endAt > :from
            """)
    List<GroupCalendarRecurrenceOverride> listOverlappingOverrides(
            @Param("groupSpaceId") Long groupSpaceId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}
