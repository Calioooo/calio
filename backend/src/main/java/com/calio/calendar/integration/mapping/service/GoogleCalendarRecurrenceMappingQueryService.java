package com.calio.calendar.integration.mapping.service;

import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.mapping.repository.GoogleCalendarRecurrenceEventMappingRepository;
import com.calio.calendar.integration.mapping.repository.GoogleCalendarRecurrenceOverrideMappingRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GoogleCalendarRecurrenceMappingQueryService {

    private final GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository;
    private final GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository;

    public GoogleCalendarRecurrenceMappingQueryService(
            GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository,
            GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository
    ) {
        this.recurrenceMappingRepository = recurrenceMappingRepository;
        this.overrideMappingRepository = overrideMappingRepository;
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

    public Optional<GoogleCalendarRecurrenceEventMapping> findRecurrenceEventMapping(
            Long recurrenceEventId
    ) {
        return recurrenceMappingRepository.findByRecurrenceEvent_Id(recurrenceEventId);
    }

    public List<GoogleCalendarRecurrenceEventMapping> listRecurrenceEventMappings(
            Long integrationId
    ) {
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

    public Optional<GoogleCalendarRecurrenceOverrideMapping> findOverrideMapping(Long overrideId) {
        return overrideMappingRepository.findByRecurrenceEventOverride_OverrideId(overrideId);
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
