package com.calio.calendar.recurrence.repository;

import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.tag.domain.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurrenceEventRepository extends JpaRepository<RecurrenceEvent, Long> {

    Optional<RecurrenceEvent> findByIdAndAccount_Id(Long id, Long accountId);

    @Query("""
            select recurrenceEvent
            from RecurrenceEvent recurrenceEvent
            where recurrenceEvent.account.id = :accountId
              and recurrenceEvent.recurrenceStartDate <= :toDate
              and recurrenceEvent.recurrenceEndDate >= :fromDate
            """)
    List<RecurrenceEvent> findRecurrenceEvents(
            @Param("accountId") Long accountId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
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
}
