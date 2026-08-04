package com.calio.calendar.integration.service;

import com.calio.calendar.integration.domain.GoogleCalendarEffectiveScope;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceOverrideMappingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleMappingConflictService {

    private final GoogleCalendarEventMappingRepository eventMappings;
    private final GoogleCalendarRecurrenceEventMappingRepository recurrenceMappings;
    private final GoogleCalendarRecurrenceOverrideMappingRepository overrideMappings;

    public GoogleMappingConflictService(
            GoogleCalendarEventMappingRepository eventMappings,
            GoogleCalendarRecurrenceEventMappingRepository recurrenceMappings,
            GoogleCalendarRecurrenceOverrideMappingRepository overrideMappings
    ) {
        this.eventMappings = eventMappings;
        this.recurrenceMappings = recurrenceMappings;
        this.overrideMappings = overrideMappings;
    }

    @Transactional(readOnly = true)
    public boolean isConflicted(Long integrationId, GoogleCalendarEffectiveScope scope) {
        return switch (scope) {
            case GoogleCalendarEffectiveScope.GeneralEvent event ->
                    eventMappings.isConflicted(integrationId, event.externalEventId());
            case GoogleCalendarEffectiveScope.RecurrenceMaster master ->
                    recurrenceMappings.isConflicted(integrationId, master.externalMasterId());
            case GoogleCalendarEffectiveScope.RecurrenceOverride override ->
                    recurrenceMappings.isConflicted(integrationId, override.externalMasterId())
                            || overrideMappings.isConflicted(integrationId,
                            override.externalMasterId(), override.originStartAt());
        };
    }

    public boolean shouldRemainLocal(
            Long integrationId,
            GoogleCalendarEffectiveScope scope
    ) {
        return isConflicted(integrationId, scope);
    }
}
