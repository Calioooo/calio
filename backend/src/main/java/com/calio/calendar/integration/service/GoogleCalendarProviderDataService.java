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
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.integration.service.GoogleCalendarSyncRunContext.RecurrenceEventOverrideExternalKey;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GoogleCalendarProviderDataService {

    private static final int RECONCILIATION_BATCH_SIZE = 500;
    private static final long FIRST_MAPPING_ID = 0L;

    private final GoogleCalendarIntegrationRepository integrationRepository;
    private final GoogleCalendarEventMappingRepository eventMappingRepository;
    private final EventRepository eventRepository;
    private final GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository;
    private final GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository;
    private final RecurrenceEventRepository recurrenceEventRepository;
    private final RecurrenceEventOverrideRepository overrideRepository;
    private final GoogleCalendarEventPagePersistenceService pagePersistenceService;
    private final TransactionTemplate transactionTemplate;

    public GoogleCalendarProviderDataService(
            GoogleCalendarIntegrationRepository integrationRepository,
            GoogleCalendarEventMappingRepository eventMappingRepository,
            EventRepository eventRepository,
            GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository,
            GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository,
            RecurrenceEventRepository recurrenceEventRepository,
            RecurrenceEventOverrideRepository overrideRepository,
            GoogleCalendarEventPagePersistenceService pagePersistenceService,
            PlatformTransactionManager transactionManager
    ) {
        this.integrationRepository = integrationRepository;
        this.eventMappingRepository = eventMappingRepository;
        this.eventRepository = eventRepository;
        this.recurrenceMappingRepository = recurrenceMappingRepository;
        this.overrideMappingRepository = overrideMappingRepository;
        this.recurrenceEventRepository = recurrenceEventRepository;
        this.overrideRepository = overrideRepository;
        this.pagePersistenceService = pagePersistenceService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public void deleteProviderData(Long integrationId) {
        deleteAllRecurrenceProviderData(integrationId);
        deleteAllEventProviderData(integrationId);
    }

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
        if (fullInventory) {
            deleteUnseenProviderData(
                    integrationId,
                    runId,
                    seenEventIds,
                    seenRecurrenceEventIds,
                    seenOverrideIds
            );
        }
        finalizeSync(integrationId, runId, nextSyncToken);
    }

    @Transactional
    public void releaseOwnedLease(Long integrationId, String runId) {
        integrationRepository.releaseSyncLease(integrationId, runId);
    }

    private void deleteUnseenProviderData(
            Long integrationId,
            String runId,
            Set<String> seenEventIds,
            Set<String> seenRecurrenceEventIds,
            Set<RecurrenceEventOverrideExternalKey> seenOverrideIds
    ) {
        deleteUnseenOverridesInBatches(integrationId, runId, seenOverrideIds);
        deleteUnseenEventsInBatches(integrationId, runId, seenEventIds);
        deleteUnseenRecurrenceEventsInBatches(
                integrationId,
                runId,
                seenRecurrenceEventIds
        );
    }

    private void deleteUnseenOverridesInBatches(
            Long integrationId,
            String runId,
            Set<RecurrenceEventOverrideExternalKey> seenOverrideIds
    ) {
        long afterId = FIRST_MAPPING_ID;
        while (true) {
            long batchAfterId = afterId;
            Long nextId = transactionTemplate.execute(status -> deleteNextOverrideBatch(
                    integrationId,
                    runId,
                    seenOverrideIds,
                    batchAfterId
            ));
            if (nextId == null) {
                return;
            }
            afterId = nextId;
        }
    }

    private Long deleteNextOverrideBatch(
            Long integrationId,
            String runId,
            Set<RecurrenceEventOverrideExternalKey> seenOverrideIds,
            long afterId
    ) {
        extendSyncLeaseOrThrow(integrationId, runId);
        List<GoogleCalendarRecurrenceOverrideMapping> mappings = overrideMappingRepository
                .findNextBatchWithRecurrenceEventMappingAndRecurrenceEventOverrideByIntegrationId(
                        integrationId,
                        afterId,
                        PageRequest.of(0, RECONCILIATION_BATCH_SIZE)
                );
        if (mappings.isEmpty()) {
            return null;
        }
        List<GoogleCalendarRecurrenceOverrideMapping> unseenMappings = mappings.stream()
                .filter(mapping -> !seenOverrideIds.contains(
                        new RecurrenceEventOverrideExternalKey(
                                mapping.getRecurrenceEventMapping().getExternalEventId(),
                                mapping.getExternalEventId()
                        )))
                .toList();
        if (!unseenMappings.isEmpty()) {
            overrideMappingRepository.deleteAllByIds(unseenMappings.stream()
                    .map(GoogleCalendarRecurrenceOverrideMapping::getId)
                    .toList());
            overrideRepository.deleteAllByIds(unseenMappings.stream()
                    .map(GoogleCalendarRecurrenceOverrideMapping::getRecurrenceEventOverride)
                    .map(RecurrenceEventOverride::getOverrideId)
                    .toList());
        }
        return mappings.getLast().getId();
    }

    private void deleteUnseenEventsInBatches(
            Long integrationId,
            String runId,
            Set<String> seenEventIds
    ) {
        long afterId = FIRST_MAPPING_ID;
        while (true) {
            long batchAfterId = afterId;
            Long nextId = transactionTemplate.execute(status -> deleteNextEventBatch(
                    integrationId,
                    runId,
                    seenEventIds,
                    batchAfterId
            ));
            if (nextId == null) {
                return;
            }
            afterId = nextId;
        }
    }

    private Long deleteNextEventBatch(
            Long integrationId,
            String runId,
            Set<String> seenEventIds,
            long afterId
    ) {
        extendSyncLeaseOrThrow(integrationId, runId);
        List<GoogleCalendarEventMapping> mappings =
                eventMappingRepository.findNextBatchWithEventByIntegrationId(
                        integrationId,
                        afterId,
                        PageRequest.of(0, RECONCILIATION_BATCH_SIZE)
                );
        if (mappings.isEmpty()) {
            return null;
        }
        List<GoogleCalendarEventMapping> unseenMappings = mappings.stream()
                .filter(mapping -> !seenEventIds.contains(mapping.getExternalEventId()))
                .toList();
        if (!unseenMappings.isEmpty()) {
            eventMappingRepository.deleteAllByIds(unseenMappings.stream()
                    .map(GoogleCalendarEventMapping::getId)
                    .toList());
            eventRepository.deleteAllByIds(unseenMappings.stream()
                    .map(GoogleCalendarEventMapping::getEvent)
                    .map(Event::getId)
                    .toList());
        }
        return mappings.getLast().getId();
    }

    private void deleteUnseenRecurrenceEventsInBatches(
            Long integrationId,
            String runId,
            Set<String> seenRecurrenceEventIds
    ) {
        long afterId = FIRST_MAPPING_ID;
        while (true) {
            long batchAfterId = afterId;
            Long nextId = transactionTemplate.execute(status -> deleteNextRecurrenceEventBatch(
                    integrationId,
                    runId,
                    seenRecurrenceEventIds,
                    batchAfterId
            ));
            if (nextId == null) {
                return;
            }
            afterId = nextId;
        }
    }

    private Long deleteNextRecurrenceEventBatch(
            Long integrationId,
            String runId,
            Set<String> seenRecurrenceEventIds,
            long afterId
    ) {
        extendSyncLeaseOrThrow(integrationId, runId);
        List<GoogleCalendarRecurrenceEventMapping> mappings = recurrenceMappingRepository
                .findNextBatchWithRecurrenceEventByIntegrationId(
                        integrationId,
                        afterId,
                        PageRequest.of(0, RECONCILIATION_BATCH_SIZE)
                );
        if (mappings.isEmpty()) {
            return null;
        }
        List<GoogleCalendarRecurrenceEventMapping> unseenMappings = mappings.stream()
                .filter(mapping -> !seenRecurrenceEventIds.contains(mapping.getExternalEventId()))
                .toList();
        if (!unseenMappings.isEmpty()) {
            List<Long> mappingIds = unseenMappings.stream()
                    .map(GoogleCalendarRecurrenceEventMapping::getId)
                    .toList();
            List<Long> recurrenceEventIds = unseenMappings.stream()
                    .map(GoogleCalendarRecurrenceEventMapping::getRecurrenceEvent)
                    .map(recurrenceEvent -> recurrenceEvent.getId())
                    .toList();
            overrideMappingRepository.deleteAllByRecurrenceEventMappingIds(mappingIds);
            overrideRepository.deleteAllByRecurrenceEventIds(recurrenceEventIds);
            recurrenceMappingRepository.deleteAllByIds(mappingIds);
            eventRepository.deleteAllByRecurrenceEventIds(recurrenceEventIds);
            recurrenceEventRepository.deleteAllByIds(recurrenceEventIds);
        }
        return mappings.getLast().getId();
    }

    private void finalizeSync(Long integrationId, String runId, String nextSyncToken) {
        transactionTemplate.executeWithoutResult(status -> {
            extendSyncLeaseOrThrow(integrationId, runId);
            if (integrationRepository.finalizeSync(integrationId, runId, nextSyncToken) != 1) {
                throw new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_CONFLICT);
            }
        });
    }

    private void extendSyncLeaseOrThrow(Long integrationId, String runId) {
        if (integrationRepository.extendSyncLease(integrationId, runId) != 1) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_CONFLICT);
        }
    }

    private void deleteAllRecurrenceProviderData(Long integrationId) {
        deleteOverrides(overrideMappingRepository
                .findAllWithRecurrenceEventMappingAndRecurrenceEventOverrideByIntegrationId(
                        integrationId
                ));
        recurrenceMappingRepository.findAllWithRecurrenceEventByIntegrationId(integrationId)
                .forEach(pagePersistenceService::deleteRecurrenceEvent);
    }

    private void deleteAllEventProviderData(Long integrationId) {
        deleteEventMappings(
                eventMappingRepository.findAllWithEventByIntegrationId(integrationId)
        );
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
