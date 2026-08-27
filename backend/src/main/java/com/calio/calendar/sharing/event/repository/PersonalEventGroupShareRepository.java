package com.calio.calendar.sharing.event.repository;

import com.calio.calendar.sharing.event.domain.PersonalEventGroupShare;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonalEventGroupShareRepository extends JpaRepository<PersonalEventGroupShare, Long> {

    Optional<PersonalEventGroupShare> findByEvent_IdAndGroupSpace_Id(Long eventId, Long groupSpaceId);

    @EntityGraph(attributePaths = {"event", "event.account", "groupSpace"})
    @Query("""
            select share
            from PersonalEventGroupShare share
            where share.event.id in :eventIds
              and share.groupSpace.id in :groupSpaceIds
            """)
    List<PersonalEventGroupShare> findAllByEventIdsAndGroupSpaceIds(
            @Param("eventIds") Collection<Long> eventIds,
            @Param("groupSpaceIds") Collection<Long> groupSpaceIds
    );

    @EntityGraph(attributePaths = {"event", "event.account", "groupSpace"})
    @Query("""
            select share
            from PersonalEventGroupShare share
            where share.groupSpace.id = :groupSpaceId
            """)
    List<PersonalEventGroupShare> findAllByGroupSpaceId(@Param("groupSpaceId") Long groupSpaceId);

    @Modifying
    @Query("""
            delete from PersonalEventGroupShare share
            where share.event.id = :eventId
            """)
    void deleteAllByEventId(@Param("eventId") Long eventId);

    @Modifying
    @Query("""
            delete from PersonalEventGroupShare share
            where share.groupSpace.id = :groupSpaceId
            """)
    void deleteAllByGroupSpaceId(@Param("groupSpaceId") Long groupSpaceId);

    @Modifying
    @Query("""
            delete from PersonalEventGroupShare share
            where share.groupSpace.id = :groupSpaceId
              and share.event.account.id = (
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
