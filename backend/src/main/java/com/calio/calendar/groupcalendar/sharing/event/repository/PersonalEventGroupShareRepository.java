package com.calio.calendar.groupcalendar.sharing.event.repository;

import com.calio.calendar.groupcalendar.sharing.event.domain.PersonalEventGroupShare;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonalEventGroupShareRepository
        extends JpaRepository<PersonalEventGroupShare, Long> {

    @EntityGraph(attributePaths = {"event", "groupSpace"})
    @Query("""
            select share
            from PersonalEventGroupShare share
            where share.event.id = :eventId
              and share.groupSpace.id = :groupSpaceId
            """)
    Optional<PersonalEventGroupShare> findByEventIdAndGroupSpaceId(
            @Param("eventId") Long eventId,
            @Param("groupSpaceId") Long groupSpaceId
    );

    @EntityGraph(attributePaths = {"event", "groupSpace"})
    @Query("""
            select share
            from PersonalEventGroupShare share
            where share.event.id = :eventId
            order by share.id
            """)
    List<PersonalEventGroupShare> findAllByEventId(@Param("eventId") Long eventId);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from PersonalEventGroupShare share
            where share.event.id = :eventId
            """)
    int deleteAllByEventId(@Param("eventId") Long eventId);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from PersonalEventGroupShare share
            where share.groupSpace.id = :groupSpaceId
              and share.event.account.id = :accountId
            """)
    int deleteAllByGroupSpaceIdAndAccountId(
            @Param("groupSpaceId") Long groupSpaceId,
            @Param("accountId") Long accountId
    );
}
