package com.calio.calendar.repository;

import com.calio.calendar.repository.entity.Event;
import com.calio.calendar.repository.entity.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByIdAndAccount_Id(Long id, Long accountId);

    Optional<Event> findByIdAndAccount_IdAndDeletedAtIsNull(Long id, Long accountId);

    List<Event> findByStartAtBetweenAndAccount_IdAndDeletedAtIsNullOrderByStartAtAsc(
            Instant from,
            Instant to,
            Long accountId
    );

    List<Event> findByRecurrenceIdAndAccount_IdAndDeletedAtIsNullOrderByStartAtAsc(Long recurrenceId, Long accountId);

    List<Event> findByRecurrenceIdAndAccount_IdOrderByStartAtAsc(Long recurrenceId, Long accountId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Event event
            set event.tag = :fallbackTag
            where event.tag = :sourceTag and event.account.id = :accountId
            """)
    int reassignAllByTagAndAccountId(
            @Param("sourceTag") Tag sourceTag,
            @Param("fallbackTag") Tag fallbackTag,
            @Param("accountId") Long accountId
    );
}
