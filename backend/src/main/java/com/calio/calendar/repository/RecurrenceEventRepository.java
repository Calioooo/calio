package com.calio.calendar.repository;

import com.calio.calendar.repository.entity.RecurrenceEvent;
import com.calio.calendar.repository.entity.Tag;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurrenceEventRepository extends JpaRepository<RecurrenceEvent, Long> {

    List<RecurrenceEvent> findByTag(Tag tag);
}
