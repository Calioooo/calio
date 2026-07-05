package com.calio.calendar.repository;

import com.calio.calendar.repository.entity.RecurrenceEvent;
import com.calio.calendar.repository.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurrenceEventRepository extends JpaRepository<RecurrenceEvent, Long> {

    @Modifying(flushAutomatically = true)
    @Query("update RecurrenceEvent recurrenceEvent set recurrenceEvent.tag = :fallbackTag where recurrenceEvent.tag = :sourceTag")
    int updateTag(@Param("sourceTag") Tag sourceTag, @Param("fallbackTag") Tag fallbackTag);
}
