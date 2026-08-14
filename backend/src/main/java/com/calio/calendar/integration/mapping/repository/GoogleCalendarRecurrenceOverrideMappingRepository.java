package com.calio.calendar.integration.mapping.repository;

import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceOverrideMapping;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface GoogleCalendarRecurrenceOverrideMappingRepository
        extends JpaRepository<GoogleCalendarRecurrenceOverrideMapping, Long> {

    @EntityGraph(attributePaths = "recurrenceEventMapping")
    @Query("""
            select overrideMapping
            from GoogleCalendarRecurrenceOverrideMapping overrideMapping
            join overrideMapping.recurrenceEventMapping recurrenceEventMapping
            where recurrenceEventMapping.integration.id = :integrationId
              and recurrenceEventMapping.calendarKey = :calendarKey
              and overrideMapping.externalEventId in :externalEventIds
            """)
    List<GoogleCalendarRecurrenceOverrideMapping>
    findAllWithRecurrenceEventMappingByExternalEventIds(
            @Param("integrationId") Long integrationId,
            @Param("calendarKey") String calendarKey,
            @Param("externalEventIds") Collection<String> externalEventIds
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"recurrenceEventMapping", "recurrenceEventOverride"})
    @Query("""
            select overrideMapping
            from GoogleCalendarRecurrenceOverrideMapping overrideMapping
            join overrideMapping.recurrenceEventMapping recurrenceEventMapping
            where recurrenceEventMapping.integration.id = :integrationId
              and recurrenceEventMapping.calendarKey = :calendarKey
              and overrideMapping.externalEventId in :externalEventIds
            order by recurrenceEventMapping.id, overrideMapping.id
            """)
    List<GoogleCalendarRecurrenceOverrideMapping>
    findAllWithRecurrenceEventMappingAndRecurrenceEventOverrideByExternalEventIdsForUpdate(
            @Param("integrationId") Long integrationId,
            @Param("calendarKey") String calendarKey,
            @Param("externalEventIds") Collection<String> externalEventIds
    );

    @EntityGraph(attributePaths = {"recurrenceEventMapping", "recurrenceEventOverride"})
    @Query("""
            select overrideMapping
            from GoogleCalendarRecurrenceOverrideMapping overrideMapping
            join overrideMapping.recurrenceEventMapping recurrenceEventMapping
            where recurrenceEventMapping.id in :recurrenceEventMappingIds
            """)
    List<GoogleCalendarRecurrenceOverrideMapping>
    findAllWithRecurrenceEventMappingAndRecurrenceEventOverrideByRecurrenceEventMappingIds(
            @Param("recurrenceEventMappingIds") Collection<Long> recurrenceEventMappingIds
    );

    @EntityGraph(attributePaths = {"recurrenceEventMapping", "recurrenceEventOverride"})
    @Query("""
            select overrideMapping
            from GoogleCalendarRecurrenceOverrideMapping overrideMapping
            join overrideMapping.recurrenceEventMapping recurrenceEventMapping
            where recurrenceEventMapping.integration.id = :integrationId
            """)
    List<GoogleCalendarRecurrenceOverrideMapping>
    findAllWithRecurrenceEventMappingAndRecurrenceEventOverrideByIntegrationId(
            @Param("integrationId") Long integrationId
    );

    @EntityGraph(attributePaths = {"recurrenceEventMapping", "recurrenceEventOverride"})
    @Query("""
            select mapping
            from GoogleCalendarRecurrenceOverrideMapping mapping
            where mapping.recurrenceEventMapping.integration.id = :integrationId
              and mapping.id > :afterId
            order by mapping.id
            """)
    List<GoogleCalendarRecurrenceOverrideMapping>
    findNextBatchWithRecurrenceEventMappingAndRecurrenceEventOverrideByIntegrationId(
            @Param("integrationId") Long integrationId,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"recurrenceEventMapping", "recurrenceEventOverride"})
    @Query("""
            select mapping
            from GoogleCalendarRecurrenceOverrideMapping mapping
            where mapping.id in :mappingIds
            order by mapping.id
            """)
    List<GoogleCalendarRecurrenceOverrideMapping>
    findAllWithRecurrenceEventMappingAndRecurrenceEventOverrideByIdsForUpdate(
            @Param("mappingIds") Collection<Long> mappingIds
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"recurrenceEventMapping", "recurrenceEventOverride"})
    @Query("""
            select mapping from GoogleCalendarRecurrenceOverrideMapping mapping
            where mapping.recurrenceEventMapping.integration.id = :integrationId
              and mapping.recurrenceEventMapping.recurrenceEvent.id = :recurrenceEventId
              and mapping.recurrenceEventOverride.originStartAt = :originStartAt
            """)
    Optional<GoogleCalendarRecurrenceOverrideMapping> findByScopeForUpdate(
            @Param("integrationId") Long integrationId,
            @Param("recurrenceEventId") Long recurrenceEventId,
            @Param("originStartAt") java.time.Instant originStartAt);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from GoogleCalendarRecurrenceOverrideMapping mapping
            where mapping.id in :mappingIds
            """)
    int deleteAllByIds(@Param("mappingIds") Collection<Long> mappingIds);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from GoogleCalendarRecurrenceOverrideMapping mapping
            where mapping.recurrenceEventMapping.id in :recurrenceEventMappingIds
            """)
    int deleteAllByRecurrenceEventMappingIds(
            @Param("recurrenceEventMappingIds") Collection<Long> recurrenceEventMappingIds
    );
}
