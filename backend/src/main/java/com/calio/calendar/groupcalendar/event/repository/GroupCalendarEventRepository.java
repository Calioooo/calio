package com.calio.calendar.groupcalendar.event.repository;

import com.calio.calendar.groupcalendar.event.domain.GroupCalendarEvent;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupCalendarEventRepository extends JpaRepository<GroupCalendarEvent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event
            from GroupCalendarEvent event
            where event.id = :eventId
              and event.groupSpace.id = :groupSpaceId
            """)
    Optional<GroupCalendarEvent> findByIdAndGroupSpaceIdForUpdate(
            @Param("groupSpaceId") Long groupSpaceId,
            @Param("eventId") Long eventId
    );

    Optional<GroupCalendarEvent> findByIdAndGroupSpace_Id(Long eventId, Long groupSpaceId);

    List<GroupCalendarEvent> findByGroupSpace_IdAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(
            Long groupSpaceId,
            Instant to,
            Instant from
    );
}
