package com.calio.calendar.integration.repository;

import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceEventMapping;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoogleCalendarRecurrenceEventMappingRepository
        extends JpaRepository<GoogleCalendarRecurrenceEventMapping, Long> {

    Optional<GoogleCalendarRecurrenceEventMapping> findByRecurrenceEvent_Id(Long recurrenceEventId);

    Optional<GoogleCalendarRecurrenceEventMapping>
    findByIntegration_IdAndCalendarKeyAndExternalEventId(
            Long integrationId,
            String calendarKey,
            String externalEventId
    );

    @EntityGraph(attributePaths = {"recurrenceEvent", "recurrenceEvent.tag"})
    @Query("""
            select mapping
            from GoogleCalendarRecurrenceEventMapping mapping
            where mapping.integration.id = :integrationId
              and mapping.calendarKey = :calendarKey
              and mapping.externalEventId in :externalEventIds
            """)
    List<GoogleCalendarRecurrenceEventMapping> findAllWithRecurrenceEventAndTagByExternalIdentity(
            @Param("integrationId") Long integrationId,
            @Param("calendarKey") String calendarKey,
            @Param("externalEventIds") Collection<String> externalEventIds
    );

    @EntityGraph(attributePaths = "recurrenceEvent")
    @Query("""
            select mapping
            from GoogleCalendarRecurrenceEventMapping mapping
            where mapping.integration.id = :integrationId
            """)
    List<GoogleCalendarRecurrenceEventMapping> findAllWithRecurrenceEventByIntegrationId(
            @Param("integrationId") Long integrationId
    );

    @EntityGraph(attributePaths = "recurrenceEvent")
    @Query("""
            select mapping
            from GoogleCalendarRecurrenceEventMapping mapping
            where mapping.integration.id = :integrationId
              and mapping.id > :afterId
            order by mapping.id
            """)
    List<GoogleCalendarRecurrenceEventMapping>
    findNextBatchWithRecurrenceEventByIntegrationId(
            @Param("integrationId") Long integrationId,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from GoogleCalendarRecurrenceEventMapping mapping
            where mapping.id in :mappingIds
            """)
    int deleteAllByIds(@Param("mappingIds") Collection<Long> mappingIds);
}
