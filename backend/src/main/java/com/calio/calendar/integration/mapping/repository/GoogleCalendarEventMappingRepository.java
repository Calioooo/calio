package com.calio.calendar.integration.mapping.repository;

import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoogleCalendarEventMappingRepository
        extends JpaRepository<GoogleCalendarEventMapping, Long> {

    @Query("""
            select mapping
            from GoogleCalendarEventMapping mapping
            where mapping.connection.id = :connectionId
              and mapping.calendarKey = :calendarKey
              and mapping.externalEventId in :externalEventIds
            """)
    List<GoogleCalendarEventMapping> findAllByExternalIdentity(
            @Param("connectionId") Long connectionId,
            @Param("calendarKey") String calendarKey,
            @Param("externalEventIds") Collection<String> externalEventIds
    );

    @EntityGraph(attributePaths = {"connection", "connection.integration"})
    @Query("""
            select mapping from GoogleCalendarEventMapping mapping
            where mapping.connection.integration.id = :integrationId
              and mapping.eventId = :eventId
            """)
    List<GoogleCalendarEventMapping> findAllWithConnectionAndIntegrationByIntegrationIdAndEventId(
            @Param("integrationId") Long integrationId,
            @Param("eventId") Long eventId
    );

    @Query("""
            select mapping.eventId
            from GoogleCalendarEventMapping mapping
            where mapping.connection.id = :connectionId
            """)
    List<Long> findEventIdsByConnectionId(@Param("connectionId") Long connectionId);

    @Query("""
            select distinct mapping.eventId
            from GoogleCalendarEventMapping mapping
            where mapping.eventId in :eventIds
            """)
    List<Long> findDistinctEventIdsByEventIdIn(@Param("eventIds") Collection<Long> eventIds);

    @Query("""
            select mapping
            from GoogleCalendarEventMapping mapping
            where mapping.connection.id = :connectionId
            """)
    List<GoogleCalendarEventMapping> findAllByConnectionId(
            @Param("connectionId") Long connectionId
    );

    @Query("""
            select mapping
            from GoogleCalendarEventMapping mapping
            where mapping.connection.id = :connectionId
              and mapping.id > :afterId
            order by mapping.id
            """)
    List<GoogleCalendarEventMapping> findNextBatchByConnectionId(
            @Param("connectionId") Long connectionId,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    @Modifying(flushAutomatically = true)
    @Query("delete from GoogleCalendarEventMapping mapping where mapping.id in :mappingIds")
    int deleteAllByIds(@Param("mappingIds") Collection<Long> mappingIds);
}
