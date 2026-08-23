package com.calio.calendar.groupcalendar.sharing.recurrence.repository;

import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonalRecurrenceGroupShareRepository extends JpaRepository<PersonalRecurrenceGroupShare, Long> {

    @EntityGraph(attributePaths = {"recurrenceEvent", "recurrenceEvent.tag", "groupSpace"})
    @Query("""
            select share
            from PersonalRecurrenceGroupShare share
            where share.recurrenceEvent.id = :recurrenceEventId
              and share.groupSpace.id = :groupSpaceId
            """)
    Optional<PersonalRecurrenceGroupShare> findByRecurrenceEventIdAndGroupSpaceId(
            @Param("recurrenceEventId") Long recurrenceEventId,
            @Param("groupSpaceId") Long groupSpaceId
    );

    @EntityGraph(attributePaths = {"recurrenceEvent", "recurrenceEvent.tag", "groupSpace"})
    @Query("""
            select share
            from PersonalRecurrenceGroupShare share
            where share.recurrenceEvent.id = :recurrenceEventId
            order by share.id
            """)
    List<PersonalRecurrenceGroupShare> findAllByRecurrenceEventId(
            @Param("recurrenceEventId") Long recurrenceEventId
    );
}
