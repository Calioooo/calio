package com.calio.calendar.integration.mapping.service;

import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.mapping.repository.GoogleCalendarEventMappingRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GoogleCalendarEventMappingCommandService {

    private final GoogleCalendarEventMappingRepository eventMappingRepository;

    public GoogleCalendarEventMappingCommandService(
            GoogleCalendarEventMappingRepository eventMappingRepository
    ) {
        this.eventMappingRepository = eventMappingRepository;
    }

    public GoogleCalendarEventMapping createEventMapping(GoogleCalendarEventMapping mapping) {
        return eventMappingRepository.save(mapping);
    }

    public void deleteEventMapping(GoogleCalendarEventMapping mapping) {
        eventMappingRepository.delete(mapping);
        eventMappingRepository.flush();
    }

    public void markLocalModification(GoogleCalendarEventMapping mapping, Instant modifiedAt) {
        mapping.markLocalModification(modifiedAt);
    }

    public void deleteEventMappings(List<GoogleCalendarEventMapping> mappings) {
        eventMappingRepository.deleteAll(mappings);
        eventMappingRepository.flush();
    }

    public void deleteEventMappingsWithIds(Collection<Long> mappingIds) {
        eventMappingRepository.deleteAllByIds(mappingIds);
    }
}
