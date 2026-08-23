package com.calio.calendar.event.repository;

import com.calio.calendar.event.domain.Event;
import com.calio.calendar.tag.domain.Tag;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByIdAndAccount_Id(Long id, Long accountId);

    @Query("""
            select event
            from Event event
            where event.id = :eventId
              and event.account.id = :accountId
              and event.recurrenceId is null
            """)
    Optional<Event> findPersonalOneOffEventByIdAndAccountId(
            @Param("eventId") Long eventId,
            @Param("accountId") Long accountId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event
            from Event event
            where event.id = :eventId
              and event.account.id = :accountId
            """)
    Optional<Event> findByIdAndAccountIdForUpdate(
            @Param("eventId") Long eventId,
            @Param("accountId") Long accountId
    );

    @Modifying(flushAutomatically = true)
    @Query("delete from Event event where event.id in :eventIds")
    int deleteAllByIds(@Param("eventIds") List<Long> eventIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from Event event where event.recurrenceId in :recurrenceEventIds")
    int deleteAllByRecurrenceEventIds(
            @Param("recurrenceEventIds") Collection<Long> recurrenceEventIds
    );

    @Query("""
            select event
            from Event event
            where event.account.id = :accountId
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

    @Query("""
            select event
            from Event event
            where event.account.id = :accountId
              and event.recurrenceId is null
            order by event.id asc
            """)
    List<Event> findAllPersonalOneOffEvents(@Param("accountId") Long accountId);

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
