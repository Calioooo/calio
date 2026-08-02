package com.calio.calendar.integration.repository;

import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoogleCalendarEventMappingRepository
        extends JpaRepository<GoogleCalendarEventMapping, Long> {

    @Query("""
            select mapping
            from GoogleCalendarEventMapping mapping
            join fetch mapping.event
            where mapping.integration.id = :integrationId
              and mapping.calendarKey = :calendarKey
              and mapping.externalEventId in :externalEventIds
            """)
    List<GoogleCalendarEventMapping> findAllByExternalIdentity(
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
            join fetch mapping.event
            where mapping.integration.id = :integrationId
            """)
    List<GoogleCalendarEventMapping> findAllByIntegrationId(
            @Param("integrationId") Long integrationId
    );
}
