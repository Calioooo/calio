package com.calio.calendar.repository;

import com.calio.calendar.repository.entity.Event;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByStartAtBetweenOrderByStartAtAsc(Instant from, Instant to);

    List<Event> findByRecurrenceIdOrderByStartAtAsc(Long recurrenceId);

    Optional<Event> findFirstByRecurrenceIdAndOriginStartAt(Long recurrenceId, Instant originStartAt);

    Optional<Event> findFirstByRecurrenceIdAndStartAt(Long recurrenceId, Instant startAt);
}
