package com.calio.calendar.sharing.recurrence.repository;

import com.calio.calendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonalRecurrenceGroupShareRepository extends JpaRepository<PersonalRecurrenceGroupShare, Long> {

    Optional<PersonalRecurrenceGroupShare> findByRecurrenceEvent_IdAndGroupSpace_Id(
            Long recurrenceEventId,
            Long groupSpaceId
    );

    @EntityGraph(attributePaths = {"recurrenceEvent", "recurrenceEvent.account", "groupSpace"})
    @Query("""
            select share
            from PersonalRecurrenceGroupShare share
            where share.recurrenceEvent.id in :recurrenceEventIds
              and share.groupSpace.id in :groupSpaceIds
            """)
    List<PersonalRecurrenceGroupShare> findAllByRecurrenceEventIdsAndGroupSpaceIds(
            @Param("recurrenceEventIds") Collection<Long> recurrenceEventIds,
            @Param("groupSpaceIds") Collection<Long> groupSpaceIds
    );

    @EntityGraph(attributePaths = {"recurrenceEvent", "recurrenceEvent.account", "groupSpace"})
    @Query("""
            select share
            from PersonalRecurrenceGroupShare share
            where share.groupSpace.id = :groupSpaceId
            """)
    List<PersonalRecurrenceGroupShare> findAllByGroupSpaceId(@Param("groupSpaceId") Long groupSpaceId);

    @Modifying
    @Query("""
            delete from PersonalRecurrenceGroupShare share
            where share.recurrenceEvent.id = :recurrenceEventId
            """)
    void deleteAllByRecurrenceEventId(@Param("recurrenceEventId") Long recurrenceEventId);

    @Modifying
    @Query("""
            delete from PersonalRecurrenceGroupShare share
            where share.groupSpace.id = :groupSpaceId
            """)
    void deleteAllByGroupSpaceId(@Param("groupSpaceId") Long groupSpaceId);

    @Modifying
    @Query("""
            delete from PersonalRecurrenceGroupShare share
            where share.groupSpace.id = :groupSpaceId
              and share.recurrenceEvent.account.id = (
                    select member.accountId
                    from GroupMember member
                    where member.id = :memberId
              )
            """)
    void deleteAllByGroupSpaceIdAndMemberId(
            @Param("groupSpaceId") Long groupSpaceId,
            @Param("memberId") Long memberId
    );
}
