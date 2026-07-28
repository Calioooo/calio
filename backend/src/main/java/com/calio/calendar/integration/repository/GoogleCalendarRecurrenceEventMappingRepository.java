package com.calio.calendar.integration.repository;

import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceEventMapping;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoogleCalendarRecurrenceEventMappingRepository
        extends JpaRepository<GoogleCalendarRecurrenceEventMapping, Long> {

    Optional<GoogleCalendarRecurrenceEventMapping> findByRecurrenceEvent_Id(Long recurrenceEventId);

    Optional<GoogleCalendarRecurrenceEventMapping>
    findByIntegration_IdAndCalendarKeyAndExternalEventId(
            Long integrationId,
            String calendarKey,
            String externalEventId
    );
}
