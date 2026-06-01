package com.calio.calendar.repository;

import com.calio.calendar.repository.entity.Event;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByStartAtBetweenOrderByStartAtAsc(Instant from, Instant to);
}
