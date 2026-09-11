package com.calio.calendar.integration.mapping.repository;

import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceEventMapping;
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

    @Query("""
            select mapping
            from GoogleCalendarRecurrenceEventMapping mapping
            where mapping.connection.id = :connectionId
              and mapping.recurrenceEventId = :recurrenceEventId
            """)
    Optional<GoogleCalendarRecurrenceEventMapping> findByConnectionIdAndRecurrenceEventId(
            @Param("connectionId") Long connectionId,
            @Param("recurrenceEventId") Long recurrenceEventId
    );

    @Query("""
            select mapping
            from GoogleCalendarRecurrenceEventMapping mapping
            join mapping.connection connection
            where connection.integration.id = :integrationId
              and connection.state <> com.calio.calendar.integration.connection.domain.GoogleCalendarConnectionState.CONNECTED
              and mapping.recurrenceEventId = :recurrenceEventId
              and mapping.localChanged = false
            """)
    List<GoogleCalendarRecurrenceEventMapping>
    findAllInactiveAndUnchangedByIntegrationIdAndRecurrenceEventId(
            @Param("integrationId") Long integrationId,
            @Param("recurrenceEventId") Long recurrenceEventId
    );

    @EntityGraph(attributePaths = "connection")
    @Query("""
            select mapping
            from GoogleCalendarRecurrenceEventMapping mapping
            where mapping.connection.id = :connectionId
              and mapping.calendarKey = :calendarKey
              and mapping.externalEventId in :externalEventIds
            """)
    List<GoogleCalendarRecurrenceEventMapping> findAllWithRecurrenceEventAndTagByExternalIdentity(
            @Param("connectionId") Long connectionId,
            @Param("calendarKey") String calendarKey,
            @Param("externalEventIds") Collection<String> externalEventIds
    );

    @EntityGraph(attributePaths = "connection")
    @Query("""
            select mapping
            from GoogleCalendarRecurrenceEventMapping mapping
            where mapping.connection.id = :connectionId
            """)
    List<GoogleCalendarRecurrenceEventMapping> findAllWithRecurrenceEventByConnectionId(
            @Param("connectionId") Long connectionId
    );

    @EntityGraph(attributePaths = "connection")
    @Query("""
            select mapping
            from GoogleCalendarRecurrenceEventMapping mapping
            where mapping.connection.id = :connectionId
              and mapping.id > :afterId
            order by mapping.id
            """)
    List<GoogleCalendarRecurrenceEventMapping>
    findNextBatchWithRecurrenceEventByConnectionId(
            @Param("connectionId") Long connectionId,
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
