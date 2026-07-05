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

    Optional<Event> findByIdAndDeletedAtIsNull(Long id);

    List<Event> findByStartAtBetweenAndDeletedAtIsNullOrderByStartAtAsc(Instant from, Instant to);

    List<Event> findByRecurrenceIdAndDeletedAtIsNullOrderByStartAtAsc(Long recurrenceId);

    List<Event> findByRecurrenceIdOrderByStartAtAsc(Long recurrenceId);

    @Modifying(flushAutomatically = true)
    @Query("update Event event set event.tag = :fallbackTag where event.tag = :sourceTag")
    int reassignAllByTag(@Param("sourceTag") Tag sourceTag, @Param("fallbackTag") Tag fallbackTag);
}
