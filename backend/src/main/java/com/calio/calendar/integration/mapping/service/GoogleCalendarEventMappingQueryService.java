package com.calio.calendar.integration.mapping.service;

import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.mapping.repository.GoogleCalendarEventMappingRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GoogleCalendarEventMappingQueryService {

    private final GoogleCalendarEventMappingRepository eventMappingRepository;

    public GoogleCalendarEventMappingQueryService(
            GoogleCalendarEventMappingRepository eventMappingRepository
    ) {
        this.eventMappingRepository = eventMappingRepository;
    }

    public List<GoogleCalendarEventMapping> listEventMappingsForEvent(
            Long integrationId, Long eventId) {
        return eventMappingRepository
                .findAllWithConnectionAndIntegrationByIntegrationIdAndEventId(integrationId, eventId);
    }

    public List<GoogleCalendarEventMapping> listEventMappings(
            Long connectionId,
            String calendarKey,
            Collection<String> externalEventIds
    ) {
        return eventMappingRepository.findAllByExternalIdentity(
                connectionId,
                calendarKey,
                externalEventIds
        );
    }

    public List<GoogleCalendarEventMapping> listEventMappings(Long connectionId) {
        return eventMappingRepository.findAllByConnectionId(connectionId);
    }

    public List<Long> listEventIdsWithMappings(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return List.of();
        }
        return eventMappingRepository.findDistinctEventIdsByEventIdIn(eventIds);
    }

    public List<GoogleCalendarEventMapping> listEventMappingBatch(
            Long connectionId,
            Long afterId,
            int limit
    ) {
        return eventMappingRepository.findNextBatchByConnectionId(
                connectionId,
                afterId,
                PageRequest.of(0, limit)
        );
    }
}
