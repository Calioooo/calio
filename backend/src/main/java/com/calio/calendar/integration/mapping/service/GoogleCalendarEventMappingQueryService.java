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
}
