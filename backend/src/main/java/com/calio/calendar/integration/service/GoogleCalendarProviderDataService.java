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

    private static final int RECONCILIATION_BATCH_SIZE = 500;
    private static final long FIRST_MAPPING_ID = 0L;

    private final GoogleCalendarIntegrationCommandService integrationCommandService;
    private final GoogleCalendarMappingQueryService mappingQueryService;
    private final GoogleCalendarMappingCommandService mappingCommandService;
    private final EventRepository eventRepository;
    private final RecurrenceEventRepository recurrenceEventRepository;
    private final RecurrenceEventOverrideRepository overrideRepository;
    private final GoogleCalendarEventPagePersistenceService pagePersistenceService;
    private final GoogleOperationJobPersistenceService operationJobPersistenceService;

    public GoogleCalendarProviderDataService(
            GoogleCalendarIntegrationCommandService integrationCommandService,
            GoogleCalendarMappingQueryService mappingQueryService,
            GoogleCalendarMappingCommandService mappingCommandService,
            EventRepository eventRepository,
            RecurrenceEventRepository recurrenceEventRepository,
            RecurrenceEventOverrideRepository overrideRepository,
            GoogleCalendarEventPagePersistenceService pagePersistenceService,
            GoogleOperationJobPersistenceService operationJobPersistenceService
    ) {
        this.integrationCommandService = integrationCommandService;
        this.mappingQueryService = mappingQueryService;
        this.mappingCommandService = mappingCommandService;
        this.eventRepository = eventRepository;
        this.recurrenceEventRepository = recurrenceEventRepository;
        this.overrideRepository = overrideRepository;
        this.pagePersistenceService = pagePersistenceService;
        this.operationJobPersistenceService = operationJobPersistenceService;
    }

    @Transactional
    public void deleteProviderData(Long integrationId) {
        deleteAllRecurrenceProviderData(integrationId);
        deleteAllEventProviderData(integrationId);
    }

    @Transactional
    public void finalizeOwnedReconciliation(
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
        renewOperationOwnership(ownership);
        if (!hasText(nextSyncToken)) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_TOKEN_MISSING);
        }
        if (syncMode == GoogleCalendarSyncMode.FULL) {
            deleteUnseenProviderData(
                    integrationId, ownership, seenEventIds,
                    seenRecurrenceEventIds, seenOverrideIds);
        }
        finalizeSync(integrationId, workerToken, nextSyncToken);
        renewOperationOwnership(ownership);
        operationJobPersistenceService.succeed(jobId, accountId, workerToken);
    }

    @Transactional
    public void releaseOwnedLease(Long integrationId, String runId) {
        integrationCommandService.releaseSyncLease(integrationId, runId);
    }

    private void deleteUnseenProviderData(
            Long integrationId,
            OperationOwnership ownership,
            Set<String> seenEventIds,
            Set<String> seenRecurrenceEventIds,
            Set<GoogleCalendarRecurrenceOverrideExternalKey> seenOverrideIds
    ) {
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
        renewReconciliationLeases(integrationId, ownership);
        List<GoogleCalendarRecurrenceOverrideMapping> mappings =
                mappingQueryService.listOverrideMappingBatch(
                        integrationId,
                        afterId,
                        RECONCILIATION_BATCH_SIZE
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
            mappingCommandService.deleteOverrideMappingsWithIds(unseenMappings.stream()
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
        renewReconciliationLeases(integrationId, ownership);
        List<GoogleCalendarEventMapping> mappings =
                mappingQueryService.listEventMappingBatch(
                        integrationId,
                        afterId,
                        RECONCILIATION_BATCH_SIZE
                );
        if (mappings.isEmpty()) {
            return null;
        }
        List<GoogleCalendarEventMapping> unseenMappings = mappings.stream()
                .filter(mapping -> !seenEventIds.contains(mapping.getExternalEventId()))
                .toList();
        if (!unseenMappings.isEmpty()) {
            mappingCommandService.deleteEventMappingsWithIds(unseenMappings.stream()
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
        renewReconciliationLeases(integrationId, ownership);
        List<GoogleCalendarRecurrenceEventMapping> mappings =
                mappingQueryService.listRecurrenceEventMappingBatch(
                        integrationId,
                        afterId,
                        RECONCILIATION_BATCH_SIZE
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
            mappingCommandService.deleteOverrideMappingsForRecurrenceMappings(mappingIds);
            overrideRepository.deleteAllByRecurrenceEventIds(recurrenceEventIds);
            mappingCommandService.deleteRecurrenceEventMappingsWithIds(mappingIds);
            eventRepository.deleteAllByRecurrenceEventIds(recurrenceEventIds);
            recurrenceEventRepository.deleteAllByIds(recurrenceEventIds);
        }
        return mappings.getLast().getId();
    }

    private void finalizeSync(Long integrationId, String runId, String nextSyncToken) {
        integrationCommandService.renewSyncLease(integrationId, runId);
        integrationCommandService.completeSync(integrationId, runId, nextSyncToken);
    }

    private void extendSyncLeaseOrThrow(Long integrationId, String runId) {
        integrationCommandService.renewSyncLease(integrationId, runId);
    }

    private void renewReconciliationLeases(
            Long integrationId,
            OperationOwnership ownership
    ) {
        renewOperationOwnership(ownership);
        extendSyncLeaseOrThrow(integrationId, ownership.workerToken());
    }

    private void renewOperationOwnership(OperationOwnership ownership) {
        operationJobPersistenceService.renewAndAssertOwned(
                ownership.jobId(),
                ownership.accountId(),
                ownership.workerToken()
        );
    }

    private void deleteAllRecurrenceProviderData(Long integrationId) {
        deleteOverrides(mappingQueryService.listOverrideMappings(integrationId));
        mappingQueryService.listRecurrenceEventMappings(integrationId)
                .forEach(pagePersistenceService::deleteRecurrenceEvent);
    }

    private void deleteAllEventProviderData(Long integrationId) {
        deleteEventMappings(
                mappingQueryService.listEventMappings(integrationId)
        );
    }

    private void deleteOverrides(List<GoogleCalendarRecurrenceOverrideMapping> mappings) {
        if (mappings.isEmpty()) {
            return;
        }
        List<RecurrenceEventOverride> overrides = mappings.stream()
                .map(GoogleCalendarRecurrenceOverrideMapping::getRecurrenceEventOverride)
                .toList();
        mappingCommandService.deleteOverrideMappings(mappings);
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
        mappingCommandService.deleteEventMappings(mappings);
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
