package com.calio.calendar.groupcalendar.sharing.recurrence.repository;

import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareSelectedOrigin;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonalRecurrenceGroupShareSelectedOriginRepository
        extends JpaRepository<PersonalRecurrenceGroupShareSelectedOrigin, Long> {

    @EntityGraph(attributePaths = "share")
    @Query("""
            select selectedOrigin
            from PersonalRecurrenceGroupShareSelectedOrigin selectedOrigin
            where selectedOrigin.share.id = :shareId
              and selectedOrigin.originStartAt = :originStartAt
            """)
    Optional<PersonalRecurrenceGroupShareSelectedOrigin> findByShareIdAndOriginStartAt(
            @Param("shareId") Long shareId,
            @Param("originStartAt") Instant originStartAt
    );

    @EntityGraph(attributePaths = "share")
    @Query("""
            select selectedOrigin
            from PersonalRecurrenceGroupShareSelectedOrigin selectedOrigin
            where selectedOrigin.share.id = :shareId
            order by selectedOrigin.originStartAt
            """)
    List<PersonalRecurrenceGroupShareSelectedOrigin> findAllByShareId(@Param("shareId") Long shareId);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from PersonalRecurrenceGroupShareSelectedOrigin selectedOrigin
            where selectedOrigin.share.recurrenceEvent.id = :recurrenceEventId
            """)
    int deleteAllByRecurrenceEventId(@Param("recurrenceEventId") Long recurrenceEventId);
}
