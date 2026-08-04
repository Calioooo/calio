package com.calio.calendar.integration.repository;

import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceOverrideMapping;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import org.springframework.data.repository.query.Param;

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

    @EntityGraph(attributePaths = {"recurrenceEventMapping", "recurrenceEventOverride"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select overrideMapping
            from GoogleCalendarRecurrenceOverrideMapping overrideMapping
            join overrideMapping.recurrenceEventMapping recurrenceEventMapping
            where recurrenceEventMapping.id in :recurrenceEventMappingIds
              and overrideMapping.externalEventId in :externalEventIds
            """)
    List<GoogleCalendarRecurrenceOverrideMapping>
    findAllWithRecurrenceEventMappingAndRecurrenceEventOverrideByExternalIdentity(
            @Param("recurrenceEventMappingIds") Collection<Long> recurrenceEventMappingIds,
            @Param("externalEventIds") Collection<String> externalEventIds
    );

    @EntityGraph(attributePaths = {"recurrenceEventMapping", "recurrenceEventOverride"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select overrideMapping
            from GoogleCalendarRecurrenceOverrideMapping overrideMapping
            join overrideMapping.recurrenceEventMapping recurrenceEventMapping
            where recurrenceEventMapping.id in :recurrenceEventMappingIds
            order by recurrenceEventMapping.id, overrideMapping.id
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
    @Lock(LockModeType.PESSIMISTIC_WRITE)
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

    @Query("""
            select count(mapping) > 0 from GoogleCalendarRecurrenceOverrideMapping mapping
            where mapping.recurrenceEventMapping.integration.id = :integrationId
              and mapping.recurrenceEventMapping.externalEventId = :externalMasterId
              and mapping.recurrenceEventOverride.originStartAt = :originStartAt
              and mapping.syncStatus = com.calio.calendar.integration.domain.GoogleCalendarMappingSyncStatus.CONFLICTED
            """)
    boolean isConflicted(@Param("integrationId") Long integrationId,
                         @Param("externalMasterId") String externalMasterId,
                         @Param("originStartAt") Instant originStartAt);
}
