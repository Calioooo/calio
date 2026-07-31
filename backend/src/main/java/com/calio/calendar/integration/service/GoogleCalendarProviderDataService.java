package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceOverrideMappingRepository;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.integration.service.GoogleCalendarSyncRunContext.OverrideIdentity;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
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

    GoogleCalendarProviderDataService(
            GoogleCalendarIntegrationRepository integrationRepository,
            GoogleCalendarEventMappingRepository eventMappingRepository,
            EventRepository eventRepository
    ) {
        this(integrationRepository, eventMappingRepository, eventRepository,
                null, null, null, null);
    }

    @Transactional
    public void deleteProviderData(Long integrationId) {
        if (recurrenceMappingRepository != null) {
            deleteAllRecurrenceProviderData(integrationId);
        }
        deleteAllGeneralProviderData(integrationId);
    }

    @Transactional
    public void finalizeReconciliation(
            Long integrationId,
            String runId,
            boolean fullInventory,
            Set<String> seenGeneralIds,
            Set<String> seenMasterIds,
            Set<OverrideIdentity> seenOverrideIds,
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
                    seenGeneralIds,
                    seenMasterIds,
                    seenOverrideIds
            );
        }
        if (integrationRepository.finalizeSync(integrationId, runId, nextSyncToken) != 1) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_CONFLICT);
        }
    }

    @Transactional
    public boolean resetUnderLease(Long integrationId, String runId) {
        if (integrationRepository.extendSyncLease(integrationId, runId) != 1) {
            return false;
        }
        return integrationRepository.clearCursorUnderLease(integrationId, runId) == 1;
    }

    @Transactional
    public void cleanupFullFailureAndRelease(Long integrationId, String runId) {
        integrationRepository.releaseSyncLease(integrationId, runId);
    }

    @Transactional
    public void releaseOwnedLease(Long integrationId, String runId) {
        integrationRepository.releaseSyncLease(integrationId, runId);
    }

    private void deleteUnseenProviderData(
            Long integrationId,
            Set<String> seenGeneralIds,
            Set<String> seenMasterIds,
            Set<OverrideIdentity> seenOverrideIds
    ) {
        List<GoogleCalendarRecurrenceOverrideMapping> unseenOverrides =
                overrideMappingRepository.findAllByIntegrationId(integrationId).stream()
                        .filter(mapping -> !seenOverrideIds.contains(new OverrideIdentity(
                                mapping.getRecurrenceEventMapping().getExternalEventId(),
                                mapping.getExternalEventId()
                        )))
                        .toList();
        deleteOverrides(unseenOverrides);

        List<GoogleCalendarEventMapping> unseenGeneralMappings =
                eventMappingRepository.findAllByIntegrationId(integrationId).stream()
                        .filter(mapping -> !seenGeneralIds.contains(mapping.getExternalEventId()))
                        .toList();
        deleteGeneralMappings(unseenGeneralMappings);

        recurrenceMappingRepository.findAllByIntegrationId(integrationId).stream()
                .filter(mapping -> !seenMasterIds.contains(mapping.getExternalEventId()))
                .forEach(pagePersistenceService::deleteMaster);
    }

    private void deleteAllRecurrenceProviderData(Long integrationId) {
        deleteOverrides(overrideMappingRepository.findAllByIntegrationId(integrationId));
        recurrenceMappingRepository.findAllByIntegrationId(integrationId)
                .forEach(pagePersistenceService::deleteMaster);
    }

    private void deleteAllGeneralProviderData(Long integrationId) {
        deleteGeneralMappings(eventMappingRepository.findAllByIntegrationId(integrationId));
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

    private void deleteGeneralMappings(List<GoogleCalendarEventMapping> mappings) {
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
