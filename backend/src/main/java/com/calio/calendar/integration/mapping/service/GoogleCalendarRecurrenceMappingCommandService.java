package com.calio.calendar.integration.mapping.service;

import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.mapping.repository.GoogleCalendarRecurrenceEventMappingRepository;
import com.calio.calendar.integration.mapping.repository.GoogleCalendarRecurrenceOverrideMappingRepository;
import java.util.Collection;
import java.util.List;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GoogleCalendarRecurrenceMappingCommandService {

    private final GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository;
    private final GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository;

    public GoogleCalendarRecurrenceMappingCommandService(
            GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository,
            GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository
    ) {
        this.recurrenceMappingRepository = recurrenceMappingRepository;
        this.overrideMappingRepository = overrideMappingRepository;
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

    public void deleteRecurrenceEventMapping(GoogleCalendarRecurrenceEventMapping mapping) {
        recurrenceMappingRepository.delete(mapping);
        recurrenceMappingRepository.flush();
    }

    public void markLocalModification(GoogleCalendarRecurrenceEventMapping mapping, Instant modifiedAt) {
        mapping.markLocalModification(modifiedAt);
    }

    public void markLocalModification(GoogleCalendarRecurrenceOverrideMapping mapping, Instant modifiedAt) {
        mapping.markLocalModification(modifiedAt);
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
