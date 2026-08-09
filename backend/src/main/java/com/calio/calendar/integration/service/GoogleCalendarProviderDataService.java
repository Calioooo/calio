package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.domain.GoogleCalendarSyncMode;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.integration.service.dto.GoogleCalendarRecurrenceOverrideExternalKey;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleCalendarProviderDataService {

    private static final int SYNC_CLEANUP_BATCH_SIZE = 500;
    private static final long FIRST_MAPPING_ID = 0L;

    private final GoogleCalendarIntegrationCommandService integrationCommandService;
    private final GoogleCalendarEventMappingQueryService eventMappingQueryService;
    private final GoogleCalendarEventMappingCommandService eventMappingCommandService;
    private final GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService;
    private final GoogleCalendarRecurrenceMappingCommandService recurrenceMappingCommandService;
    private final EventRepository eventRepository;
    private final RecurrenceEventRepository recurrenceEventRepository;
    private final RecurrenceEventOverrideRepository overrideRepository;
    private final GoogleCalendarEventPagePersistenceService pagePersistenceService;
    private final GoogleOperationLeaseService operationLeaseService;
    private final GoogleOperationJobPersistenceService operationJobPersistenceService;

    public GoogleCalendarProviderDataService(
            GoogleCalendarIntegrationCommandService integrationCommandService,
            GoogleCalendarEventMappingQueryService eventMappingQueryService,
            GoogleCalendarEventMappingCommandService eventMappingCommandService,
            GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService,
            GoogleCalendarRecurrenceMappingCommandService recurrenceMappingCommandService,
            EventRepository eventRepository,
            RecurrenceEventRepository recurrenceEventRepository,
            RecurrenceEventOverrideRepository overrideRepository,
            GoogleCalendarEventPagePersistenceService pagePersistenceService,
            GoogleOperationLeaseService operationLeaseService,
            GoogleOperationJobPersistenceService operationJobPersistenceService
    ) {
        this.integrationCommandService = integrationCommandService;
        this.eventMappingQueryService = eventMappingQueryService;
        this.eventMappingCommandService = eventMappingCommandService;
        this.recurrenceMappingQueryService = recurrenceMappingQueryService;
        this.recurrenceMappingCommandService = recurrenceMappingCommandService;
        this.eventRepository = eventRepository;
        this.recurrenceEventRepository = recurrenceEventRepository;
        this.overrideRepository = overrideRepository;
        this.pagePersistenceService = pagePersistenceService;
        this.operationLeaseService = operationLeaseService;
        this.operationJobPersistenceService = operationJobPersistenceService;
    }

    @Transactional
    public void deleteProviderData(Long integrationId) {
        deleteAllRecurrenceProviderData(integrationId);
        deleteAllEventProviderData(integrationId);
    }

    @Transactional
    /**
     * Completes one sync run atomically.
     *
     * <p>FULL sync cleanup, the next sync token, and operation job completion must commit
     * together so a sync token never represents a partial provider data update.</p>
     */
    public void completeSyncRun(
            Long jobId,
            Long accountId,
            Long integrationId,
            String workerToken,
            GoogleCalendarSyncMode syncMode,
            Set<String> seenEventIds,
            Set<String> seenRecurrenceEventIds,
            Set<GoogleCalendarRecurrenceOverrideExternalKey> seenOverrideIds,
            String nextSyncToken
    ) {
        OperationOwnership ownership = new OperationOwnership(jobId, accountId, workerToken);
        operationLeaseService.extend(ownership.jobId(), ownership.accountId(), ownership.workerToken());
        requireNextSyncToken(nextSyncToken);
        removeProviderDataMissingFromFullSync(
                integrationId,
                ownership,
                syncMode,
                seenEventIds,
                seenRecurrenceEventIds,
                seenOverrideIds
        );
        integrationCommandService.saveNextSyncToken(integrationId, nextSyncToken);
        completeOperationJob(ownership);
    }

    private void requireNextSyncToken(String nextSyncToken) {
        if (!hasText(nextSyncToken)) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_TOKEN_MISSING);
        }
    }

    private void removeProviderDataMissingFromFullSync(
            Long integrationId,
            OperationOwnership ownership,
            GoogleCalendarSyncMode syncMode,
            Set<String> seenEventIds,
            Set<String> seenRecurrenceEventIds,
            Set<GoogleCalendarRecurrenceOverrideExternalKey> seenOverrideIds
    ) {
        if (syncMode != GoogleCalendarSyncMode.FULL) {
            return;
        }
        deleteUnseenOverridesInBatches(integrationId, ownership, seenOverrideIds);
        deleteUnseenEventsInBatches(integrationId, ownership, seenEventIds);
        deleteUnseenRecurrenceEventsInBatches(
                integrationId,
                ownership,
                seenRecurrenceEventIds
        );
    }

    private void deleteUnseenOverridesInBatches(
            Long integrationId,
            OperationOwnership ownership,
            Set<GoogleCalendarRecurrenceOverrideExternalKey> seenOverrideIds
    ) {
        long afterId = FIRST_MAPPING_ID;
        while (true) {
            Long nextId = deleteNextOverrideBatch(
                    integrationId,
                    ownership,
                    seenOverrideIds,
                    afterId
            );
            if (nextId == null) {
                return;
            }
            afterId = nextId;
        }
    }

    private Long deleteNextOverrideBatch(
            Long integrationId,
            OperationOwnership ownership,
            Set<GoogleCalendarRecurrenceOverrideExternalKey> seenOverrideIds,
            long afterId
    ) {
        operationLeaseService.extend(ownership.jobId(), ownership.accountId(), ownership.workerToken());
        List<GoogleCalendarRecurrenceOverrideMapping> mappings =
                recurrenceMappingQueryService.listOverrideMappingBatch(
                        integrationId,
                        afterId,
                        SYNC_CLEANUP_BATCH_SIZE
                );
        if (mappings.isEmpty()) {
            return null;
        }
        List<GoogleCalendarRecurrenceOverrideMapping> unseenMappings = mappings.stream()
                .filter(mapping -> !seenOverrideIds.contains(
                        new GoogleCalendarRecurrenceOverrideExternalKey(
                                mapping.getRecurrenceEventMapping().getExternalEventId(),
                                mapping.getExternalEventId()
                        )))
                .toList();
        if (!unseenMappings.isEmpty()) {
            recurrenceMappingCommandService.deleteOverrideMappingsWithIds(unseenMappings.stream()
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
            OperationOwnership ownership,
            Set<String> seenEventIds
    ) {
        long afterId = FIRST_MAPPING_ID;
        while (true) {
            Long nextId = deleteNextEventBatch(
                    integrationId,
                    ownership,
                    seenEventIds,
                    afterId
            );
            if (nextId == null) {
                return;
            }
            afterId = nextId;
        }
    }

    private Long deleteNextEventBatch(
            Long integrationId,
            OperationOwnership ownership,
            Set<String> seenEventIds,
            long afterId
    ) {
        operationLeaseService.extend(ownership.jobId(), ownership.accountId(), ownership.workerToken());
        List<GoogleCalendarEventMapping> mappings =
                eventMappingQueryService.listEventMappingBatch(
                        integrationId,
                        afterId,
                        SYNC_CLEANUP_BATCH_SIZE
                );
        if (mappings.isEmpty()) {
            return null;
        }
        List<GoogleCalendarEventMapping> unseenMappings = mappings.stream()
                .filter(mapping -> !seenEventIds.contains(mapping.getExternalEventId()))
                .toList();
        if (!unseenMappings.isEmpty()) {
            eventMappingCommandService.deleteEventMappingsWithIds(unseenMappings.stream()
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
            OperationOwnership ownership,
            Set<String> seenRecurrenceEventIds
    ) {
        long afterId = FIRST_MAPPING_ID;
        while (true) {
            Long nextId = deleteNextRecurrenceEventBatch(
                    integrationId,
                    ownership,
                    seenRecurrenceEventIds,
                    afterId
            );
            if (nextId == null) {
                return;
            }
            afterId = nextId;
        }
    }

    private Long deleteNextRecurrenceEventBatch(
            Long integrationId,
            OperationOwnership ownership,
            Set<String> seenRecurrenceEventIds,
            long afterId
    ) {
        operationLeaseService.extend(ownership.jobId(), ownership.accountId(), ownership.workerToken());
        List<GoogleCalendarRecurrenceEventMapping> mappings =
                recurrenceMappingQueryService.listRecurrenceEventMappingBatch(
                        integrationId,
                        afterId,
                        SYNC_CLEANUP_BATCH_SIZE
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
            recurrenceMappingCommandService.deleteOverrideMappingsForRecurrenceMappings(
                    mappingIds
            );
            overrideRepository.deleteAllByRecurrenceEventIds(recurrenceEventIds);
            recurrenceMappingCommandService.deleteRecurrenceEventMappingsWithIds(mappingIds);
            eventRepository.deleteAllByRecurrenceEventIds(recurrenceEventIds);
            recurrenceEventRepository.deleteAllByIds(recurrenceEventIds);
        }
        return mappings.getLast().getId();
    }

    private void completeOperationJob(OperationOwnership ownership) {
        operationLeaseService.extend(ownership.jobId(), ownership.accountId(), ownership.workerToken());
        operationJobPersistenceService.succeed(
                ownership.jobId(),
                ownership.accountId(),
                ownership.workerToken()
        );
    }

    private void deleteAllRecurrenceProviderData(Long integrationId) {
        deleteOverrides(recurrenceMappingQueryService.listOverrideMappings(integrationId));
        recurrenceMappingQueryService.listRecurrenceEventMappings(integrationId)
                .forEach(pagePersistenceService::deleteRecurrenceEvent);
    }

    private void deleteAllEventProviderData(Long integrationId) {
        deleteEventMappings(
                eventMappingQueryService.listEventMappings(integrationId)
        );
    }

    private void deleteOverrides(List<GoogleCalendarRecurrenceOverrideMapping> mappings) {
        if (mappings.isEmpty()) {
            return;
        }
        List<RecurrenceEventOverride> overrides = mappings.stream()
                .map(GoogleCalendarRecurrenceOverrideMapping::getRecurrenceEventOverride)
                .toList();
        recurrenceMappingCommandService.deleteOverrideMappings(mappings);
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
        eventMappingCommandService.deleteEventMappings(mappings);
        eventRepository.deleteAll(events);
        eventRepository.flush();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record OperationOwnership(
            Long jobId,
            Long accountId,
            String workerToken
    ) {
    }
}
