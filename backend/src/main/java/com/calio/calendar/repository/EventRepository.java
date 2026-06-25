package com.calio.calendar.repository;

import com.calio.calendar.repository.entity.Event;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByIdAndDeletedAtIsNull(Long id);

    List<Event> findByStartAtBetweenAndDeletedAtIsNullOrderByStartAtAsc(Instant from, Instant to);

    List<Event> findByRecurrenceIdOrderByStartAtAsc(Long recurrenceId);
}
