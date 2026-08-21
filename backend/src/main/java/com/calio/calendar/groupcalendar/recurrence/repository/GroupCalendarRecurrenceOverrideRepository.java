package com.calio.calendar.groupcalendar.recurrence.repository;

import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceOverride;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupCalendarRecurrenceOverrideRepository
        extends JpaRepository<GroupCalendarRecurrenceOverride, Long> {

    @EntityGraph(attributePaths = {"recurrenceEvent", "recurrenceEvent.tag"})
    Optional<GroupCalendarRecurrenceOverride> findByRecurrenceEvent_IdAndOriginStartAt(
            Long recurrenceId,
            Instant originStartAt
    );

    @EntityGraph(attributePaths = {"recurrenceEvent", "recurrenceEvent.tag"})
    List<GroupCalendarRecurrenceOverride> findByRecurrenceEvent_IdAndOriginStartAtIn(
            Long recurrenceId,
            Collection<Instant> originStartAts
    );

    @EntityGraph(attributePaths = {"recurrenceEvent", "recurrenceEvent.tag"})
    List<GroupCalendarRecurrenceOverride>
    findByRecurrenceEvent_GroupSpace_IdAndDeletedAtIsNullAndStartAtLessThanAndEndAtGreaterThan(
            Long groupSpaceId,
            Instant to,
            Instant from
    );
}
