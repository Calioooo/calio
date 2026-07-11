package com.calio.calendar.repository;

import com.calio.calendar.repository.entity.RecurrenceEvent;
import com.calio.calendar.repository.entity.Tag;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurrenceEventRepository extends JpaRepository<RecurrenceEvent, Long> {

    Optional<RecurrenceEvent> findByIdAndAccount_Id(Long id, Long accountId);

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
