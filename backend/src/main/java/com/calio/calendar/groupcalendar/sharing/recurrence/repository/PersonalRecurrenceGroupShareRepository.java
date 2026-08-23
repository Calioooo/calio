package com.calio.calendar.groupcalendar.sharing.recurrence.repository;

import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    @EntityGraph(attributePaths = {"recurrenceEvent", "recurrenceEvent.tag", "groupSpace"})
    @Query("""
            select share
            from PersonalRecurrenceGroupShare share
            where share.groupSpace.id = :groupSpaceId
            order by share.id
            """)
    List<PersonalRecurrenceGroupShare> findAllByGroupSpaceId(@Param("groupSpaceId") Long groupSpaceId);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from PersonalRecurrenceGroupShare share
            where share.recurrenceEvent.id = :recurrenceEventId
            """)
    int deleteAllByRecurrenceEventId(@Param("recurrenceEventId") Long recurrenceEventId);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from PersonalRecurrenceGroupShare share
            where share.groupSpace.id = :groupSpaceId
              and share.recurrenceEvent.account.id = :accountId
            """)
    int deleteAllByGroupSpaceIdAndAccountId(
            @Param("groupSpaceId") Long groupSpaceId,
            @Param("accountId") Long accountId
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from PersonalRecurrenceGroupShare share
            where share.groupSpace.id = :groupSpaceId
            """)
    int deleteAllByGroupSpaceId(@Param("groupSpaceId") Long groupSpaceId);
}
