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

    Optional<GoogleCalendarRecurrenceEventMapping> findByConnection_IdAndRecurrenceEventId(
            Long connectionId, Long recurrenceEventId);

    @Query("""
            select mapping from GoogleCalendarRecurrenceEventMapping mapping
            join fetch mapping.connection connection
            join fetch connection.integration integration
            where integration.id = :integrationId
              and mapping.recurrenceEventId = :recurrenceEventId
            """)
    List<GoogleCalendarRecurrenceEventMapping> findAllForJob(
            @Param("integrationId") Long integrationId,
            @Param("recurrenceEventId") Long recurrenceEventId);

    @Query("""
            select distinct mapping.recurrenceEventId
            from GoogleCalendarRecurrenceEventMapping mapping
            where mapping.recurrenceEventId in :recurrenceEventIds
            """)
    List<Long> findRecurrenceEventIdsWithMappings(
            @Param("recurrenceEventIds") Collection<Long> recurrenceEventIds);

    Optional<GoogleCalendarRecurrenceEventMapping>
    findByConnection_IdAndCalendarKeyAndExternalEventId(
            Long connectionId,
            String calendarKey,
            String externalEventId
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
