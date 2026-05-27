package com.calio.calendar.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.calio.calendar.repository.entity.Event;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByStartAtBetweenOrderByStartAtAsc(Instant from, Instant to);
}
