package com.calio.calendar.repository;

import com.calio.calendar.repository.entity.RecurrenceEventOverride;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurrenceEventOverrideRepository extends JpaRepository<RecurrenceEventOverride, Long> {

    Optional<RecurrenceEventOverride> findByRecurrenceIdAndOriginStartAt(Long recurrenceId, Instant originStartAt);
}
