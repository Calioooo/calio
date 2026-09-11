package com.calio.calendar.integration.mapping.service;

import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.mapping.repository.GoogleCalendarRecurrenceEventMappingRepository;
import com.calio.calendar.integration.mapping.repository.GoogleCalendarRecurrenceOverrideMappingRepository;
import java.time.Instant;
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
            Long connectionId,
            String calendarKey,
            Collection<String> externalEventIds
    ) {
        return recurrenceMappingRepository.findAllWithRecurrenceEventAndTagByExternalIdentity(
                connectionId,
                calendarKey,
                externalEventIds
        );
    }

    public Optional<GoogleCalendarRecurrenceEventMapping> getRecurrenceEventMappingIfExists(
            Long connectionId, Long recurrenceEventId
    ) {
        return recurrenceMappingRepository.findByConnectionIdAndRecurrenceEventId(
                connectionId,
                recurrenceEventId
        );
    }

    public Optional<GoogleCalendarRecurrenceEventMapping> getRecurrenceEventMappingIfExists(
            Long mappingId
    ) {
        return recurrenceMappingRepository.findById(mappingId);
    }

    public Optional<GoogleCalendarRecurrenceOverrideMapping> getOverrideMappingIfExists(
            Long recurrenceEventMappingId, Instant originStartAt
    ) {
        return overrideMappingRepository.findByRecurrenceEventMappingIdAndOriginStartAt(
                recurrenceEventMappingId, originStartAt);
    }

    public List<GoogleCalendarRecurrenceEventMapping> listRecurrenceEventMappings(
            Long connectionId
    ) {
        return recurrenceMappingRepository.findAllWithRecurrenceEventByConnectionId(connectionId);
    }

    public List<GoogleCalendarRecurrenceEventMapping> listRecurrenceEventMappingBatch(
            Long connectionId,
            Long afterId,
            int limit
    ) {
        return recurrenceMappingRepository.findNextBatchWithRecurrenceEventByConnectionId(
                connectionId,
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
            Long connectionId,
            String calendarKey,
            Collection<String> externalEventIds
    ) {
        return overrideMappingRepository.findAllWithRecurrenceEventMappingByExternalEventIds(
                connectionId,
                calendarKey,
                externalEventIds
        );
    }

    public List<GoogleCalendarRecurrenceOverrideMapping> listOverrideMappings(Long connectionId) {
        return overrideMappingRepository
                .findAllWithRecurrenceEventMappingAndRecurrenceEventOverrideByConnectionId(
                        connectionId
                );
    }

    public List<GoogleCalendarRecurrenceOverrideMapping> listOverrideMappingBatch(
            Long connectionId,
            Long afterId,
            int limit
    ) {
        return overrideMappingRepository
                .findNextBatchWithRecurrenceEventMappingAndRecurrenceEventOverrideByConnectionId(
                        connectionId,
                        afterId,
                        PageRequest.of(0, limit)
                );
    }
}
