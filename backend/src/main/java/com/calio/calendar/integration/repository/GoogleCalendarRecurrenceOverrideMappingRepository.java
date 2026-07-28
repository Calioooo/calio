package com.calio.calendar.integration.repository;

import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceOverrideMapping;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoogleCalendarRecurrenceOverrideMappingRepository
        extends JpaRepository<GoogleCalendarRecurrenceOverrideMapping, Long> {

    Optional<GoogleCalendarRecurrenceOverrideMapping>
    findByRecurrenceEventOverride_OverrideId(Long recurrenceEventOverrideId);

    Optional<GoogleCalendarRecurrenceOverrideMapping>
    findByRecurrenceEventMapping_IdAndExternalEventId(
            Long recurrenceEventMappingId,
            String externalEventId
    );

    @Query("""
            select overrideMapping
            from GoogleCalendarRecurrenceOverrideMapping overrideMapping
            join fetch overrideMapping.recurrenceEventMapping parentMapping
            where parentMapping.integration.id = :integrationId
              and parentMapping.calendarKey = :calendarKey
              and overrideMapping.externalEventId = :externalEventId
            """)
    List<GoogleCalendarRecurrenceOverrideMapping> findAllByExternalIdentity(
            @Param("integrationId") Long integrationId,
            @Param("calendarKey") String calendarKey,
            @Param("externalEventId") String externalEventId
    );
}
