package com.calio.calendar.recurrence.repository;

import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.tag.domain.Tag;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurrenceEventRepository extends JpaRepository<RecurrenceEvent, Long> {

    Optional<RecurrenceEvent> findByIdAndAccount_Id(Long id, Long accountId);

    @EntityGraph(attributePaths = "tag")
    @Query("""
            select recurrenceEvent
            from RecurrenceEvent recurrenceEvent
            where recurrenceEvent.account.id = :accountId
              and recurrenceEvent.firstOccurrenceStartAt < :to
            """)
    List<RecurrenceEvent> findExpansionCandidatesStartedBefore(
            @Param("accountId") Long accountId,
            @Param("to") Instant to
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select recurrenceEvent
            from RecurrenceEvent recurrenceEvent
            where recurrenceEvent.id = :recurrenceId
              and recurrenceEvent.account.id = :accountId
            """)
    Optional<RecurrenceEvent> findByIdAndAccountIdForUpdate(
            @Param("recurrenceId") Long recurrenceId,
            @Param("accountId") Long accountId
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            update RecurrenceEvent recurrenceEvent
            set recurrenceEvent.tag = :fallbackTag
            where recurrenceEvent.tag = :sourceTag and recurrenceEvent.account.id = :accountId
            """)
    int reassignAllByTagAndAccountId(
            @Param("sourceTag") Tag sourceTag,
            @Param("fallbackTag") Tag fallbackTag,
            @Param("accountId") Long accountId
    );

    @Modifying(flushAutomatically = true)
    @Query("delete from RecurrenceEvent recurrenceEvent where recurrenceEvent.id in :ids")
    int deleteAllByIds(@Param("ids") Collection<Long> ids);
}
