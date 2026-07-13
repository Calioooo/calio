package com.calio.calendar.event.repository;

import com.calio.calendar.event.domain.Event;
import com.calio.calendar.tag.domain.Tag;
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

    @Query("""
            select event
            from Event event
            where event.account.id = :accountId
              and event.deletedAt is null
              and event.recurrenceId is null
              and event.startAt < :to
              and event.endAt > :from
            order by event.startAt asc
            """)
    List<Event> findNormalEvents(
            @Param("accountId") Long accountId,
            @Param("from") Instant from,
            @Param("to") Instant to
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
