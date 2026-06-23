package com.calio.calendar.repository;

import com.calio.calendar.repository.entity.RecurrenceEventOverride;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurrenceEventOverrideRepository extends JpaRepository<RecurrenceEventOverride, Long> {

    Optional<RecurrenceEventOverride> findByEventId(Long eventId);

    boolean existsByEventId(Long eventId);
}
