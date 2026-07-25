package com.calio.calendar.integration.service;

import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleCalendarProviderDataService {

    private final GoogleCalendarIntegrationRepository integrationRepository;
    private final GoogleCalendarEventMappingRepository mappingRepository;
    private final EventRepository eventRepository;

    public GoogleCalendarProviderDataService(
            GoogleCalendarIntegrationRepository integrationRepository,
            GoogleCalendarEventMappingRepository mappingRepository,
            EventRepository eventRepository
    ) {
        this.integrationRepository = integrationRepository;
        this.mappingRepository = mappingRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public void deleteProviderData(Long integrationId) {
        deleteMappingsThenEvents(integrationId);
    }

    @Transactional
    public boolean resetUnderLease(Long integrationId, String runId) {
        if (!extendLease(integrationId, runId)) {
            return false;
        }
        if (integrationRepository.clearCursorUnderLease(integrationId, runId) != 1) {
            return false;
        }
        deleteMappingsThenEvents(integrationId);
        return true;
    }

    @Transactional
    public void cleanupFullFailureAndRelease(Long integrationId, String runId) {
        if (extendLease(integrationId, runId)) {
            deleteMappingsThenEvents(integrationId);
        }
        integrationRepository.releaseSyncLease(integrationId, runId);
    }

    @Transactional
    public void releaseOwnedLease(Long integrationId, String runId) {
        integrationRepository.releaseSyncLease(integrationId, runId);
    }

    private boolean extendLease(Long integrationId, String runId) {
        return integrationRepository.extendSyncLease(integrationId, runId) == 1;
    }

    private void deleteMappingsThenEvents(Long integrationId) {
        List<Long> eventIds = mappingRepository.findEventIdsByIntegrationId(integrationId);
        mappingRepository.deleteAllByIntegrationId(integrationId);
        if (!eventIds.isEmpty()) {
            eventRepository.deleteAllByIds(eventIds);
        }
    }
}
