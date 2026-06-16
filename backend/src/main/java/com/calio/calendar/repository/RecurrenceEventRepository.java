package com.calio.calendar.repository;

import com.calio.calendar.repository.entity.RecurrenceEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurrenceEventRepository extends JpaRepository<RecurrenceEvent, Long> {
}
