package com.calio.calendar.integration.service;

import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceOverrideMappingRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GoogleCalendarMappingQueryService {

    private final GoogleCalendarEventMappingRepository eventMappingRepository;
    private final GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository;
    private final GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository;

    public GoogleCalendarMappingQueryService(
            GoogleCalendarEventMappingRepository eventMappingRepository,
            GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository,
            GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository
    ) {
        this.eventMappingRepository = eventMappingRepository;
        this.recurrenceMappingRepository = recurrenceMappingRepository;
        this.overrideMappingRepository = overrideMappingRepository;
    }

    public boolean hasExternalEventMapping(Long eventId, Long accountId) {
        return eventMappingRepository.existsByEvent_IdAndIntegration_AccountId(eventId, accountId);
    }

    public List<GoogleCalendarEventMapping> listEventMappings(
            Long integrationId,
            String calendarKey,
            Collection<String> externalEventIds
    ) {
        return eventMappingRepository.findAllWithEventByExternalIdentity(
                integrationId,
                calendarKey,
                externalEventIds
        );
    }

    public List<GoogleCalendarEventMapping> listEventMappings(Long integrationId) {
        return eventMappingRepository.findAllWithEventByIntegrationId(integrationId);
    }

    public List<GoogleCalendarEventMapping> listEventMappingBatch(
            Long integrationId,
            Long afterId,
            int limit
    ) {
        return eventMappingRepository.findNextBatchWithEventByIntegrationId(
                integrationId,
                afterId,
                PageRequest.of(0, limit)
        );
    }

    public List<GoogleCalendarRecurrenceEventMapping> listRecurrenceEventMappings(
            Long integrationId,
            String calendarKey,
            Collection<String> externalEventIds
    ) {
        return recurrenceMappingRepository.findAllWithRecurrenceEventAndTagByExternalIdentity(
                integrationId,
                calendarKey,
                externalEventIds
        );
    }

    public List<GoogleCalendarRecurrenceEventMapping> listRecurrenceEventMappings(Long integrationId) {
        return recurrenceMappingRepository.findAllWithRecurrenceEventByIntegrationId(integrationId);
    }

    public List<GoogleCalendarRecurrenceEventMapping> listRecurrenceEventMappingBatch(
            Long integrationId,
            Long afterId,
            int limit
    ) {
        return recurrenceMappingRepository.findNextBatchWithRecurrenceEventByIntegrationId(
                integrationId,
                afterId,
                PageRequest.of(0, limit)
        );
    }

    public List<GoogleCalendarRecurrenceOverrideMapping> listOverrideMappings(
            Collection<Long> recurrenceEventMappingIds
    ) {
        return overrideMappingRepository
                .findAllWithRecurrenceEventMappingAndRecurrenceEventOverrideByRecurrenceEventMappingIds(
                        recurrenceEventMappingIds
                );
    }

    public List<GoogleCalendarRecurrenceOverrideMapping> listOverrideMappings(
            Long integrationId,
            String calendarKey,
            Collection<String> externalEventIds
    ) {
        return overrideMappingRepository.findAllWithRecurrenceEventMappingByExternalEventIds(
                integrationId,
                calendarKey,
                externalEventIds
        );
    }

    public List<GoogleCalendarRecurrenceOverrideMapping> listOverrideMappings(Long integrationId) {
        return overrideMappingRepository
                .findAllWithRecurrenceEventMappingAndRecurrenceEventOverrideByIntegrationId(
                        integrationId
                );
    }

    public List<GoogleCalendarRecurrenceOverrideMapping> listOverrideMappingBatch(
            Long integrationId,
            Long afterId,
            int limit
    ) {
        return overrideMappingRepository
                .findNextBatchWithRecurrenceEventMappingAndRecurrenceEventOverrideByIntegrationId(
                        integrationId,
                        afterId,
                        PageRequest.of(0, limit)
                );
    }
}
