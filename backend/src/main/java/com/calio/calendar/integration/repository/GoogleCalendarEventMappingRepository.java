package com.calio.calendar.integration.repository;

import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

public interface GoogleCalendarEventMappingRepository
        extends JpaRepository<GoogleCalendarEventMapping, Long> {

    @EntityGraph(attributePaths = "event")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select mapping
            from GoogleCalendarEventMapping mapping
            where mapping.integration.id = :integrationId
              and mapping.calendarKey = :calendarKey
              and mapping.externalEventId in :externalEventIds
            order by mapping.id
            """)
    List<GoogleCalendarEventMapping> findAllWithEventByExternalIdentity(
            @Param("integrationId") Long integrationId,
            @Param("calendarKey") String calendarKey,
            @Param("externalEventIds") Collection<String> externalEventIds
    );

    boolean existsByEvent_IdAndIntegration_AccountId(Long eventId, Long accountId);

    @Query("""
            select mapping.event.id
            from GoogleCalendarEventMapping mapping
            where mapping.integration.id = :integrationId
            """)
    List<Long> findEventIdsByIntegrationId(@Param("integrationId") Long integrationId);

    @Query("""
            select mapping
            from GoogleCalendarEventMapping mapping
            where mapping.integration.id = :integrationId
            """)
    List<GoogleCalendarEventMapping> findAllByIntegrationId(
            @Param("integrationId") Long integrationId
    );

    @EntityGraph(attributePaths = "event")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select mapping
            from GoogleCalendarEventMapping mapping
            where mapping.integration.id = :integrationId
            """)
    List<GoogleCalendarEventMapping> findAllWithEventByIntegrationId(
            @Param("integrationId") Long integrationId
    );

    @EntityGraph(attributePaths = "event")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select mapping
            from GoogleCalendarEventMapping mapping
            where mapping.integration.id = :integrationId
              and mapping.id > :afterId
            order by mapping.id
            """)
    List<GoogleCalendarEventMapping> findNextBatchWithEventByIntegrationId(
            @Param("integrationId") Long integrationId,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    @Modifying(flushAutomatically = true)
    @Query("delete from GoogleCalendarEventMapping mapping where mapping.id in :mappingIds")
    int deleteAllByIds(@Param("mappingIds") Collection<Long> mappingIds);

    @Query("""
            select count(mapping) > 0 from GoogleCalendarEventMapping mapping
            where mapping.integration.id = :integrationId
              and mapping.externalEventId = :externalEventId
              and mapping.syncStatus = com.calio.calendar.integration.domain.GoogleCalendarMappingSyncStatus.CONFLICTED
            """)
    boolean isConflicted(@Param("integrationId") Long integrationId,
                         @Param("externalEventId") String externalEventId);
}
