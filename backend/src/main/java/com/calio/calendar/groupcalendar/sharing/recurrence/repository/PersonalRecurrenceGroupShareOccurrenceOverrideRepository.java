package com.calio.calendar.groupcalendar.sharing.recurrence.repository;

import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareOccurrenceOverride;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonalRecurrenceGroupShareOccurrenceOverrideRepository
        extends JpaRepository<PersonalRecurrenceGroupShareOccurrenceOverride, Long> {

    @EntityGraph(attributePaths = "share")
    @Query("""
            select occurrenceOverride
            from PersonalRecurrenceGroupShareOccurrenceOverride occurrenceOverride
            where occurrenceOverride.share.id = :shareId
              and occurrenceOverride.originStartAt = :originStartAt
            """)
    Optional<PersonalRecurrenceGroupShareOccurrenceOverride> findByShareIdAndOriginStartAt(
            @Param("shareId") Long shareId,
            @Param("originStartAt") Instant originStartAt
    );

    @EntityGraph(attributePaths = "share")
    @Query("""
            select occurrenceOverride
            from PersonalRecurrenceGroupShareOccurrenceOverride occurrenceOverride
            where occurrenceOverride.share.id = :shareId
            """)
    java.util.List<PersonalRecurrenceGroupShareOccurrenceOverride> findAllByShareId(
            @Param("shareId") Long shareId
    );

    @Modifying(flushAutomatically = true)
    @Query("delete from PersonalRecurrenceGroupShareOccurrenceOverride occurrenceOverride where occurrenceOverride.share.id = :shareId")
    int deleteAllByShareId(@Param("shareId") Long shareId);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from PersonalRecurrenceGroupShareOccurrenceOverride occurrenceOverride
            where occurrenceOverride.share.recurrenceEvent.id = :recurrenceEventId
            """)
    int deleteAllByRecurrenceEventId(@Param("recurrenceEventId") Long recurrenceEventId);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from PersonalRecurrenceGroupShareOccurrenceOverride occurrenceOverride
            where occurrenceOverride.share.groupSpace.id = :groupSpaceId
              and occurrenceOverride.share.recurrenceEvent.account.id = :accountId
            """)
    int deleteAllByGroupSpaceIdAndAccountId(
            @Param("groupSpaceId") Long groupSpaceId,
            @Param("accountId") Long accountId
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from PersonalRecurrenceGroupShareOccurrenceOverride occurrenceOverride
            where occurrenceOverride.share.groupSpace.id = :groupSpaceId
            """)
    int deleteAllByGroupSpaceId(@Param("groupSpaceId") Long groupSpaceId);
}
