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

    @EntityGraph(attributePaths = "event")
    @Query("""
            select mapping
            from GoogleCalendarEventMapping mapping
            where mapping.connection.id = :connectionId
              and mapping.calendarKey = :calendarKey
              and mapping.externalEventId in :externalEventIds
            """)
    List<GoogleCalendarEventMapping> findAllWithEventByExternalIdentity(
            @Param("connectionId") Long connectionId,
            @Param("calendarKey") String calendarKey,
            @Param("externalEventIds") Collection<String> externalEventIds
    );

    boolean existsByEvent_IdAndConnection_Integration_AccountId(Long eventId, Long accountId);

    @Query("""
            select mapping.event.id
            from GoogleCalendarEventMapping mapping
            where mapping.connection.id = :connectionId
            """)
    List<Long> findEventIdsByConnectionId(@Param("connectionId") Long connectionId);

    @EntityGraph(attributePaths = "event")
    @Query("""
            select mapping
            from GoogleCalendarEventMapping mapping
            where mapping.connection.id = :connectionId
            """)
    List<GoogleCalendarEventMapping> findAllWithEventByConnectionId(
            @Param("connectionId") Long connectionId
    );

    @EntityGraph(attributePaths = "event")
    @Query("""
            select mapping
            from GoogleCalendarEventMapping mapping
            where mapping.connection.id = :connectionId
              and mapping.id > :afterId
            order by mapping.id
            """)
    List<GoogleCalendarEventMapping> findNextBatchWithEventByConnectionId(
            @Param("connectionId") Long connectionId,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    @Modifying(flushAutomatically = true)
    @Query("delete from GoogleCalendarEventMapping mapping where mapping.id in :mappingIds")
    int deleteAllByIds(@Param("mappingIds") Collection<Long> mappingIds);
}
