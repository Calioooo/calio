package com.calio.calendar.integration.repository;

import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoogleCalendarEventMappingRepository
        extends JpaRepository<GoogleCalendarEventMapping, Long> {

    Optional<GoogleCalendarEventMapping>
            findByIntegration_IdAndCalendarKeyAndExternalEventId(
                    Long integrationId,
                    String calendarKey,
                    String externalEventId
            );

    boolean existsByEvent_IdAndIntegration_AccountId(Long eventId, Long accountId);

    @Query("""
            select mapping.event.id
            from GoogleCalendarEventMapping mapping
            where mapping.integration.id = :integrationId
            """)
    List<Long> findEventIdsByIntegrationId(@Param("integrationId") Long integrationId);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from GoogleCalendarEventMapping mapping
            where mapping.integration.id = :integrationId
            """)
    int deleteAllByIntegrationId(@Param("integrationId") Long integrationId);
}
