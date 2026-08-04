package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.domain.GoogleCalendarSyncMode;
import com.calio.calendar.integration.domain.GoogleCalendarMappingSyncStatus;
import com.calio.calendar.integration.domain.GoogleCalendarEffectiveScope;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceOverrideMappingRepository;
import com.calio.calendar.integration.repository.GoogleOperationJobRepository;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.integration.service.GoogleCalendarSyncRunContext.RecurrenceEventOverrideExternalKey;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

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
    private final GoogleOperationJobPersistenceService operationJobPersistenceService;
    private final GoogleOperationJobRepository operationJobRepository;

    @Autowired
    public GoogleCalendarProviderDataService(
            GoogleCalendarIntegrationRepository integrationRepository,
            GoogleCalendarEventMappingRepository eventMappingRepository,
            EventRepository eventRepository,
            GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository,
            GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository,
            RecurrenceEventRepository recurrenceEventRepository,
            RecurrenceEventOverrideRepository overrideRepository,
            GoogleCalendarEventPagePersistenceService pagePersistenceService,
            GoogleOperationJobPersistenceService operationJobPersistenceService,
            GoogleOperationJobRepository operationJobRepository
    ) {
        this.integrationRepository = integrationRepository;
        this.eventMappingRepository = eventMappingRepository;
        this.eventRepository = eventRepository;
        this.recurrenceMappingRepository = recurrenceMappingRepository;
        this.overrideMappingRepository = overrideMappingRepository;
        this.recurrenceEventRepository = recurrenceEventRepository;
        this.overrideRepository = overrideRepository;
        this.pagePersistenceService = pagePersistenceService;
        this.operationJobPersistenceService = operationJobPersistenceService;
        this.operationJobRepository = operationJobRepository;
    }

    GoogleCalendarProviderDataService(
            GoogleCalendarIntegrationRepository integrationRepository,
            GoogleCalendarEventMappingRepository eventMappingRepository,
            EventRepository eventRepository,
            GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository,
            GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository,
            RecurrenceEventRepository recurrenceEventRepository,
            RecurrenceEventOverrideRepository overrideRepository,
            GoogleCalendarEventPagePersistenceService pagePersistenceService,
            GoogleOperationJobPersistenceService operationJobPersistenceService
    ) {
        this(integrationRepository, eventMappingRepository, eventRepository,
                recurrenceMappingRepository, overrideMappingRepository,
                recurrenceEventRepository, overrideRepository, pagePersistenceService,
                operationJobPersistenceService, null);
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
            Set<RecurrenceEventOverrideExternalKey> seenOverrideIds,
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
        integrationRepository.releaseSyncLease(integrationId, runId);
    }

    private void deleteUnseenProviderData(
            Long integrationId,
            OperationOwnership ownership,
            Set<String> seenEventIds,
            Set<String> seenRecurrenceEventIds,
            Set<RecurrenceEventOverrideExternalKey> seenOverrideIds
    ) {
        deleteUnseenRecurrenceEventsInBatches(
                integrationId,
                ownership,
                seenRecurrenceEventIds
        );
        deleteUnseenOverridesInBatches(integrationId, ownership, seenOverrideIds);
        deleteUnseenEventsInBatches(integrationId, ownership, seenEventIds);
    }

    private void deleteUnseenOverridesInBatches(
            Long integrationId,
            OperationOwnership ownership,
            Set<RecurrenceEventOverrideExternalKey> seenOverrideIds
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
            Set<RecurrenceEventOverrideExternalKey> seenOverrideIds,
            long afterId
    ) {
        renewReconciliationLeases(integrationId, ownership);
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
                .filter(mapping -> canDeleteUnseenOverride(
                        integrationId, mapping, ownership))
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
                .filter(mapping -> canDeleteUnseenEvent(
                        integrationId, mapping, ownership))
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
                .filter(mapping -> canDeleteUnseenMaster(
                        integrationId, mapping, ownership))
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
        extendSyncLeaseOrThrow(integrationId, runId);
        if (integrationRepository.finalizeSync(integrationId, runId, nextSyncToken) != 1) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_CONFLICT);
        }
    }

    private boolean canDeleteUnseenEvent(
            Long integrationId,
            GoogleCalendarEventMapping mapping,
            OperationOwnership ownership
    ) {
        if (mapping.getSyncStatus() == GoogleCalendarMappingSyncStatus.CONFLICTED) {
            return false;
        }
        return quarantineIfPending(integrationId, mapping,
                GoogleCalendarEffectiveScope.generalEvent(mapping.getExternalEventId()), ownership);
    }

    private boolean canDeleteUnseenMaster(
            Long integrationId,
            GoogleCalendarRecurrenceEventMapping mapping,
            OperationOwnership ownership
    ) {
        if (mapping.getSyncStatus() == GoogleCalendarMappingSyncStatus.CONFLICTED) {
            return false;
        }
        GoogleCalendarEffectiveScope.RecurrenceMaster scope =
                GoogleCalendarEffectiveScope.recurrenceMaster(mapping.getExternalEventId());
        if (!hasPendingMasterOrChild(ownership, integrationId, scope)) {
            return true;
        }
        mapping.markConflicted();
        operationJobPersistenceService.markConflictDetected(
                ownership.jobId(), ownership.accountId(), ownership.workerToken());
        return false;
    }

    private boolean canDeleteUnseenOverride(
            Long integrationId,
            GoogleCalendarRecurrenceOverrideMapping mapping,
            OperationOwnership ownership
    ) {
        if (mapping.getSyncStatus() == GoogleCalendarMappingSyncStatus.CONFLICTED
                || mapping.getRecurrenceEventMapping().getSyncStatus()
                == GoogleCalendarMappingSyncStatus.CONFLICTED) {
            return false;
        }
        GoogleCalendarEffectiveScope scope = GoogleCalendarEffectiveScope.recurrenceOverride(
                mapping.getRecurrenceEventMapping().getExternalEventId(),
                mapping.getRecurrenceEventOverride().getOriginStartAt());
        if (!hasPending(ownership, integrationId, scope)) {
            return true;
        }
        mapping.markConflicted();
        operationJobPersistenceService.markConflictDetected(
                ownership.jobId(), ownership.accountId(), ownership.workerToken());
        return false;
    }

    private boolean quarantineIfPending(
            Long integrationId,
            Object mapping,
            GoogleCalendarEffectiveScope scope,
            OperationOwnership ownership
    ) {
        if (!hasPending(ownership, integrationId, scope)) {
            return true;
        }
        if (mapping instanceof GoogleCalendarEventMapping event) {
            event.markConflicted();
        } else {
            ((GoogleCalendarRecurrenceEventMapping) mapping).markConflicted();
        }
        operationJobPersistenceService.markConflictDetected(
                ownership.jobId(), ownership.accountId(), ownership.workerToken());
        return false;
    }

    private boolean hasPending(
            OperationOwnership ownership,
            Long integrationId,
            GoogleCalendarEffectiveScope scope
    ) {
        return operationJobRepository != null
                && !operationJobRepository.findPendingDesiredContentHashes(
                ownership.accountId(), integrationId,
                scope.encodedType(), scope.encodedKey()).isEmpty();
    }

    private boolean hasPendingMasterOrChild(
            OperationOwnership ownership,
            Long integrationId,
            GoogleCalendarEffectiveScope.RecurrenceMaster scope
    ) {
        if (operationJobRepository == null) {
            return false;
        }
        String childPrefix = GoogleCalendarEffectiveScope.recurrenceOverrideKeyPrefix(
                scope.externalMasterId());
        return operationJobRepository.countPendingRecurrenceAggregateBranches(
                ownership.accountId(), integrationId, scope.encodedKey(),
                childPrefix, childPrefix.length()) > 0;
    }

    private void extendSyncLeaseOrThrow(Long integrationId, String runId) {
        if (integrationRepository.extendSyncLease(integrationId, runId) != 1) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_CONFLICT);
        }
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

    private record OperationOwnership(
            Long jobId,
            Long accountId,
            String workerToken
    ) {
    }
}
