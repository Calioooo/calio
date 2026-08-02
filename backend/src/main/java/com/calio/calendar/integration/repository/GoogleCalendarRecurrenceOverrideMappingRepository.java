package com.calio.calendar.integration.repository;

import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceOverrideMapping;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
              and overrideMapping.externalEventId = :externalEventId
            """)
    List<GoogleCalendarRecurrenceOverrideMapping>
    findAllWithRecurrenceEventMappingByExternalIdentity(
            @Param("integrationId") Long integrationId,
            @Param("calendarKey") String calendarKey,
            @Param("externalEventId") String externalEventId
    );

    @EntityGraph(attributePaths = {"recurrenceEventMapping", "recurrenceEventOverride"})
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
}
