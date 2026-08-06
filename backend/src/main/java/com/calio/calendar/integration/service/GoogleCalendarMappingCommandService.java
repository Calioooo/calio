package com.calio.calendar.integration.service;

import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceOverrideMappingRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GoogleCalendarMappingCommandService {

    private final GoogleCalendarEventMappingRepository eventMappingRepository;
    private final GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository;
    private final GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository;

    public GoogleCalendarMappingCommandService(
            GoogleCalendarEventMappingRepository eventMappingRepository,
            GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository,
            GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository
    ) {
        this.eventMappingRepository = eventMappingRepository;
        this.recurrenceMappingRepository = recurrenceMappingRepository;
        this.overrideMappingRepository = overrideMappingRepository;
    }

    public GoogleCalendarEventMapping createEventMapping(GoogleCalendarEventMapping mapping) {
        return eventMappingRepository.save(mapping);
    }

    public GoogleCalendarRecurrenceEventMapping createRecurrenceEventMapping(
            GoogleCalendarRecurrenceEventMapping mapping
    ) {
        return recurrenceMappingRepository.saveAndFlush(mapping);
    }

    public GoogleCalendarRecurrenceOverrideMapping createOverrideMapping(
            GoogleCalendarRecurrenceOverrideMapping mapping
    ) {
        return overrideMappingRepository.save(mapping);
    }

    public void deleteEventMapping(GoogleCalendarEventMapping mapping) {
        eventMappingRepository.delete(mapping);
        eventMappingRepository.flush();
    }

    public void deleteEventMappings(List<GoogleCalendarEventMapping> mappings) {
        eventMappingRepository.deleteAll(mappings);
        eventMappingRepository.flush();
    }

    public void deleteEventMappingsWithIds(Collection<Long> mappingIds) {
        eventMappingRepository.deleteAllByIds(mappingIds);
    }

    public void deleteRecurrenceEventMapping(GoogleCalendarRecurrenceEventMapping mapping) {
        recurrenceMappingRepository.delete(mapping);
        recurrenceMappingRepository.flush();
    }

    public void deleteRecurrenceEventMappingsWithIds(Collection<Long> mappingIds) {
        recurrenceMappingRepository.deleteAllByIds(mappingIds);
    }

    public void deleteOverrideMappings(List<GoogleCalendarRecurrenceOverrideMapping> mappings) {
        overrideMappingRepository.deleteAll(mappings);
        overrideMappingRepository.flush();
    }

    public void deleteOverrideMappingsWithIds(Collection<Long> mappingIds) {
        overrideMappingRepository.deleteAllByIds(mappingIds);
    }

    public void deleteOverrideMappingsForRecurrenceMappings(Collection<Long> mappingIds) {
        overrideMappingRepository.deleteAllByRecurrenceEventMappingIds(mappingIds);
    }
}
