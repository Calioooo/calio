package com.calio.calendar.integration.mapping.repository;

import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceOverrideMapping;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoogleCalendarRecurrenceOverrideMappingRepository
        extends JpaRepository<GoogleCalendarRecurrenceOverrideMapping, Long> {

    @EntityGraph(attributePaths = "recurrenceEventMapping")
    @Query("""
            select overrideMapping
            from GoogleCalendarRecurrenceOverrideMapping overrideMapping
            join overrideMapping.recurrenceEventMapping recurrenceEventMapping
            where recurrenceEventMapping.connection.id = :connectionId
              and recurrenceEventMapping.calendarKey = :calendarKey
              and overrideMapping.externalEventId in :externalEventIds
            """)
    List<GoogleCalendarRecurrenceOverrideMapping>
    findAllWithRecurrenceEventMappingByExternalEventIds(
            @Param("connectionId") Long connectionId,
            @Param("calendarKey") String calendarKey,
            @Param("externalEventIds") Collection<String> externalEventIds
    );

    @Query("""
            select mapping
            from GoogleCalendarRecurrenceOverrideMapping mapping
            where mapping.recurrenceEventMapping.id = :recurrenceEventMappingId
              and mapping.originStartAt = :originStartAt
            """)
    Optional<GoogleCalendarRecurrenceOverrideMapping> findByRecurrenceEventMappingIdAndOriginStartAt(
            @Param("recurrenceEventMappingId") Long recurrenceEventMappingId,
            @Param("originStartAt") Instant originStartAt
    );

    @Query("""
            select overrideMapping
            from GoogleCalendarRecurrenceOverrideMapping overrideMapping
            join overrideMapping.recurrenceEventMapping recurrenceEventMapping
            join recurrenceEventMapping.connection connection
            where connection.integration.id = :integrationId
              and connection.state <> com.calio.calendar.integration.connection.domain.GoogleCalendarConnectionState.CONNECTED
              and recurrenceEventMapping.recurrenceEventId = :recurrenceEventId
              and overrideMapping.originStartAt = :originStartAt
              and overrideMapping.localChanged = false
            """)
    List<GoogleCalendarRecurrenceOverrideMapping> findAllInactiveAndUnchangedByIdentity(
            @Param("integrationId") Long integrationId,
            @Param("recurrenceEventId") Long recurrenceEventId,
            @Param("originStartAt") Instant originStartAt
    );

    @EntityGraph(attributePaths = "recurrenceEventMapping")
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

    @EntityGraph(attributePaths = "recurrenceEventMapping")
    @Query("""
            select overrideMapping
            from GoogleCalendarRecurrenceOverrideMapping overrideMapping
            join overrideMapping.recurrenceEventMapping recurrenceEventMapping
            where recurrenceEventMapping.connection.id = :connectionId
            """)
    List<GoogleCalendarRecurrenceOverrideMapping>
    findAllWithRecurrenceEventMappingAndRecurrenceEventOverrideByConnectionId(
            @Param("connectionId") Long connectionId
    );

    @EntityGraph(attributePaths = "recurrenceEventMapping")
    @Query("""
            select mapping
            from GoogleCalendarRecurrenceOverrideMapping mapping
            where mapping.recurrenceEventMapping.connection.id = :connectionId
              and mapping.id > :afterId
            order by mapping.id
            """)
    List<GoogleCalendarRecurrenceOverrideMapping>
    findNextBatchWithRecurrenceEventMappingAndRecurrenceEventOverrideByConnectionId(
            @Param("connectionId") Long connectionId,
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
}
