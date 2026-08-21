package com.calio.calendar.groupcalendar.recurrence.repository;

import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceEvent;
import com.calio.calendar.tag.domain.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface GroupCalendarRecurrenceEventRepository extends JpaRepository<GroupCalendarRecurrenceEvent, Long> {

    Optional<GroupCalendarRecurrenceEvent> findByIdAndGroupSpace_Id(Long id, Long groupSpaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from GroupCalendarRecurrenceEvent event where event.id = :id and event.groupSpace.id = :groupSpaceId")
    Optional<GroupCalendarRecurrenceEvent> findByIdAndGroupSpaceIdForUpdate(
            @Param("id") Long id,
            @Param("groupSpaceId") Long groupSpaceId
    );

    List<GroupCalendarRecurrenceEvent> findByGroupSpace_IdAndFirstOccurrenceStartAtLessThan(Long groupSpaceId, Instant to);

    @Modifying
    @Query("update GroupCalendarRecurrenceEvent event set event.tag = :targetTag where event.tag = :sourceTag")
    void reassignAllByTag(@Param("sourceTag") Tag sourceTag, @Param("targetTag") Tag targetTag);

    void deleteAllByGroupSpace_Id(Long groupSpaceId);

    void deleteAllByGroupSpace_IdAndCreatedBy_Id(Long groupSpaceId, Long accountId);
}
