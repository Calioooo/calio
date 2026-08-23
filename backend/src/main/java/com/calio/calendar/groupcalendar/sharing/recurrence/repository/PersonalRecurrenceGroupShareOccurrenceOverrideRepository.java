package com.calio.calendar.groupcalendar.sharing.recurrence.repository;

import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareOccurrenceOverride;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
