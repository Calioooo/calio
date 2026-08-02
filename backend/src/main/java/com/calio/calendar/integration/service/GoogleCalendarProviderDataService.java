package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceOverrideMappingRepository;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.integration.service.GoogleCalendarSyncRunContext.RecurrenceEventOverrideExternalKey;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleCalendarProviderDataService {

    private final GoogleCalendarIntegrationRepository integrationRepository;
    private final GoogleCalendarEventMappingRepository eventMappingRepository;
    private final EventRepository eventRepository;
    private final GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository;
    private final GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository;
    private final RecurrenceEventOverrideRepository overrideRepository;
    private final GoogleCalendarEventPagePersistenceService pagePersistenceService;

    public GoogleCalendarProviderDataService(
            GoogleCalendarIntegrationRepository integrationRepository,
            GoogleCalendarEventMappingRepository eventMappingRepository,
            EventRepository eventRepository,
            GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository,
            GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository,
            RecurrenceEventOverrideRepository overrideRepository,
            GoogleCalendarEventPagePersistenceService pagePersistenceService
    ) {
        this.integrationRepository = integrationRepository;
        this.eventMappingRepository = eventMappingRepository;
        this.eventRepository = eventRepository;
        this.recurrenceMappingRepository = recurrenceMappingRepository;
        this.overrideMappingRepository = overrideMappingRepository;
        this.overrideRepository = overrideRepository;
        this.pagePersistenceService = pagePersistenceService;
    }

    @Transactional
    public void deleteProviderData(Long integrationId) {
        deleteAllRecurrenceProviderData(integrationId);
        deleteAllEventProviderData(integrationId);
    }

    @Transactional
    public void finalizeReconciliation(
            Long integrationId,
            String runId,
            boolean fullInventory,
            Set<String> seenEventIds,
            Set<String> seenRecurrenceEventIds,
            Set<RecurrenceEventOverrideExternalKey> seenOverrideIds,
            String nextSyncToken
    ) {
        if (!hasText(nextSyncToken)) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_TOKEN_MISSING);
        }
        if (integrationRepository.extendSyncLease(integrationId, runId) != 1) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_CONFLICT);
        }
        if (fullInventory) {
            deleteUnseenProviderData(
                    integrationId,
                    seenEventIds,
                    seenRecurrenceEventIds,
                    seenOverrideIds
            );
        }
        if (integrationRepository.finalizeSync(integrationId, runId, nextSyncToken) != 1) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_CONFLICT);
        }
    }

    @Transactional
    public void releaseOwnedLease(Long integrationId, String runId) {
        integrationRepository.releaseSyncLease(integrationId, runId);
    }

    private void deleteUnseenProviderData(
            Long integrationId,
            Set<String> seenEventIds,
            Set<String> seenRecurrenceEventIds,
            Set<RecurrenceEventOverrideExternalKey> seenOverrideIds
    ) {
        List<GoogleCalendarRecurrenceOverrideMapping> unseenOverrides =
                overrideMappingRepository.findAllByIntegrationId(integrationId).stream()
                        .filter(mapping -> !seenOverrideIds.contains(
                                new RecurrenceEventOverrideExternalKey(
                                        mapping.getRecurrenceEventMapping().getExternalEventId(),
                                        mapping.getExternalEventId()
                                )))
                        .toList();
        deleteOverrides(unseenOverrides);

        List<GoogleCalendarEventMapping> unseenEventMappings =
                eventMappingRepository.findAllByIntegrationId(integrationId).stream()
                        .filter(mapping -> !seenEventIds.contains(mapping.getExternalEventId()))
                        .toList();
        deleteEventMappings(unseenEventMappings);

        recurrenceMappingRepository.findAllByIntegrationId(integrationId).stream()
                .filter(mapping -> !seenRecurrenceEventIds.contains(mapping.getExternalEventId()))
                .forEach(pagePersistenceService::deleteRecurrenceEvent);
    }

    private void deleteAllRecurrenceProviderData(Long integrationId) {
        deleteOverrides(overrideMappingRepository.findAllByIntegrationId(integrationId));
        recurrenceMappingRepository.findAllByIntegrationId(integrationId)
                .forEach(pagePersistenceService::deleteRecurrenceEvent);
    }

    private void deleteAllEventProviderData(Long integrationId) {
        deleteEventMappings(eventMappingRepository.findAllByIntegrationId(integrationId));
    }

    private void deleteOverrides(List<GoogleCalendarRecurrenceOverrideMapping> mappings) {
        if (mappings.isEmpty()) {
            return;
        }
        List<RecurrenceEventOverride> overrides = mappings.stream()
                .map(GoogleCalendarRecurrenceOverrideMapping::getRecurrenceEventOverride)
                .toList();
        overrideMappingRepository.deleteAll(mappings);
        overrideMappingRepository.flush();
        overrideRepository.deleteAll(overrides);
        overrideRepository.flush();
    }

    private void deleteEventMappings(List<GoogleCalendarEventMapping> mappings) {
        if (mappings.isEmpty()) {
            return;
        }
        List<Event> events = mappings.stream()
                .map(GoogleCalendarEventMapping::getEvent)
                .toList();
        eventMappingRepository.deleteAll(mappings);
        eventMappingRepository.flush();
        eventRepository.deleteAll(events);
        eventRepository.flush();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
