package com.calio.calendar.integration.sync;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.service.EventCommandService;
import com.calio.calendar.integration.connection.service.GoogleCalendarIntegrationCommandService;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarContentHasher;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarMappingSyncStatus;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingCommandService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingQueryService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingCommandService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationLeaseService;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEffectiveScope;
import com.calio.calendar.integration.sync.page.GoogleCalendarRecurrenceChangeService;
import com.calio.calendar.recurrence.service.RecurrenceEventCommandService;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarRecurrenceOverrideExternalKey;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleCalendarIntegrationDataService {

    private static final int SYNC_CLEANUP_BATCH_SIZE = 500;
    private static final long FIRST_MAPPING_ID = 0L;

    private final GoogleCalendarIntegrationCommandService integrationCommandService;
    private final GoogleCalendarEventMappingQueryService eventMappingQueryService;
    private final GoogleCalendarEventMappingCommandService eventMappingCommandService;
    private final GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService;
    private final GoogleCalendarRecurrenceMappingCommandService recurrenceMappingCommandService;
    private final EventCommandService eventCommandService;
    private final RecurrenceEventCommandService recurrenceEventCommandService;
    private final GoogleCalendarRecurrenceChangeService recurrenceChangeService;
    private final GoogleOperationLeaseService operationLeaseService;
    private final GoogleOperationJobService operationJobService;
    private final GoogleOperationJobQueryService operationJobQueryService;

    public GoogleCalendarIntegrationDataService(
            GoogleCalendarIntegrationCommandService integrationCommandService,
            GoogleCalendarEventMappingQueryService eventMappingQueryService,
            GoogleCalendarEventMappingCommandService eventMappingCommandService,
            GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService,
            GoogleCalendarRecurrenceMappingCommandService recurrenceMappingCommandService,
            EventCommandService eventCommandService,
            RecurrenceEventCommandService recurrenceEventCommandService,
            GoogleCalendarRecurrenceChangeService recurrenceChangeService,
            GoogleOperationLeaseService operationLeaseService,
            GoogleOperationJobService operationJobService,
            GoogleOperationJobQueryService operationJobQueryService
    ) {
        this.integrationCommandService = integrationCommandService;
        this.eventMappingQueryService = eventMappingQueryService;
        this.eventMappingCommandService = eventMappingCommandService;
        this.recurrenceMappingQueryService = recurrenceMappingQueryService;
        this.recurrenceMappingCommandService = recurrenceMappingCommandService;
        this.eventCommandService = eventCommandService;
        this.recurrenceEventCommandService = recurrenceEventCommandService;
        this.recurrenceChangeService = recurrenceChangeService;
        this.operationLeaseService = operationLeaseService;
        this.operationJobService = operationJobService;
        this.operationJobQueryService = operationJobQueryService;
    }

    @Transactional
    public void deleteIntegrationData(Long integrationId) {
        deleteAllMappedRecurrenceData(integrationId);
        deleteAllMappedEventData(integrationId);
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
        removeDataMissingFromFullSync(
                integrationId,
                ownership,
                syncMode,
                seenEventIds,
                seenRecurrenceEventIds,
                seenOverrideIds
        );
        integrationCommandService.changeNextSyncToken(integrationId, nextSyncToken);
        completeOperationJob(ownership);
    }

    private void requireNextSyncToken(String nextSyncToken) {
        if (!hasText(nextSyncToken)) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_TOKEN_MISSING);
        }
    }

    private void removeDataMissingFromFullSync(
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
                        ))).filter(mapping -> canDeleteUnseen(mapping, ownership))
                .toList();
        if (!unseenMappings.isEmpty()) {
            recurrenceMappingCommandService.deleteOverrideMappingsWithIds(unseenMappings.stream()
                    .map(GoogleCalendarRecurrenceOverrideMapping::getId)
                    .toList());
            recurrenceEventCommandService.deleteRecurrenceOverridesByIds(unseenMappings.stream()
                    .map(GoogleCalendarRecurrenceOverrideMapping::getRecurrenceEventOverride)
                    .map(override -> override.getOverrideId())
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
                .filter(mapping -> canDeleteUnseen(mapping, ownership))
                .toList();
        if (!unseenMappings.isEmpty()) {
            eventMappingCommandService.deleteEventMappingsWithIds(unseenMappings.stream()
                    .map(GoogleCalendarEventMapping::getId)
                    .toList());
            eventCommandService.deleteEventsByIds(unseenMappings.stream()
                    .map(GoogleCalendarEventMapping::getEvent)
                    .map(event -> event.getId())
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
                .filter(mapping -> canDeleteUnseen(mapping, ownership))
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
            recurrenceMappingCommandService.deleteRecurrenceEventMappingsWithIds(mappingIds);
            recurrenceEventCommandService.deleteRecurrenceOverridesByRecurrenceEventIds(recurrenceEventIds);
            eventCommandService.deleteEventsByRecurrenceEventIds(recurrenceEventIds);
            recurrenceEventCommandService.deleteRecurrenceEventsByIds(recurrenceEventIds);
        }
        return mappings.getLast().getId();
    }

    private void completeOperationJob(OperationOwnership ownership) {
        operationLeaseService.extend(ownership.jobId(), ownership.accountId(), ownership.workerToken());
        operationJobService.completeSyncRun(
                ownership.jobId(),
                ownership.accountId(),
                ownership.workerToken()
        );
    }

    private boolean canDeleteUnseen(
            GoogleCalendarEventMapping mapping,
            OperationOwnership ownership
    ) {
        if (mapping.getSyncStatus() == GoogleCalendarMappingSyncStatus.CONFLICTED) {
            return false;
        }
        GoogleCalendarEffectiveScope scope = GoogleCalendarEffectiveScope.event(
                mapping.getEvent().getId());
        if (GoogleCalendarContentHasher.hash(mapping.getEvent())
                .equals(mapping.getSyncedContentHash())
                && operationJobQueryService.listPendingDesiredContentHashes(
                        mapping.getIntegration().getAccountId(), mapping.getIntegration().getId(), scope)
                .isEmpty()) {
            return true;
        }
        mapping.markConflicted();
        operationJobService.recordSyncConflict(ownership.jobId(), ownership.workerToken());
        return false;
    }

    private boolean canDeleteUnseen(
            GoogleCalendarRecurrenceEventMapping mapping,
            OperationOwnership ownership
    ) {
        if (mapping.getSyncStatus() == GoogleCalendarMappingSyncStatus.CONFLICTED) {
            return false;
        }
        GoogleCalendarEffectiveScope scope = GoogleCalendarEffectiveScope.recurrenceEvent(
                mapping.getRecurrenceEvent().getId());
        if (GoogleCalendarContentHasher.hash(mapping.getRecurrenceEvent())
                .equals(mapping.getSyncedContentHash())
                && operationJobQueryService.listPendingDesiredContentHashes(
                        mapping.getIntegration().getAccountId(), mapping.getIntegration().getId(), scope)
                .isEmpty()) {
            return true;
        }
        mapping.markConflicted();
        operationJobService.recordSyncConflict(ownership.jobId(), ownership.workerToken());
        return false;
    }

    private boolean canDeleteUnseen(
            GoogleCalendarRecurrenceOverrideMapping mapping,
            OperationOwnership ownership
    ) {
        if (mapping.getSyncStatus() == GoogleCalendarMappingSyncStatus.CONFLICTED
                || mapping.getRecurrenceEventMapping().getSyncStatus()
                == GoogleCalendarMappingSyncStatus.CONFLICTED) {
            return false;
        }
        GoogleCalendarEffectiveScope scope = GoogleCalendarEffectiveScope.recurrenceOverride(
                mapping.getRecurrenceEventMapping().getRecurrenceEvent().getId(),
                mapping.getRecurrenceEventOverride().getOriginStartAt());
        if (GoogleCalendarContentHasher.hash(
                mapping.getRecurrenceEventMapping().getExternalEventId(),
                mapping.getRecurrenceEventOverride()).equals(mapping.getSyncedContentHash())
                && operationJobQueryService.listPendingDesiredContentHashes(
                        mapping.getRecurrenceEventMapping().getIntegration().getAccountId(),
                        mapping.getRecurrenceEventMapping().getIntegration().getId(), scope).isEmpty()) {
            return true;
        }
        mapping.markConflicted();
        operationJobService.recordSyncConflict(ownership.jobId(), ownership.workerToken());
        return false;
    }

    private void deleteAllMappedRecurrenceData(Long integrationId) {
        deleteOverrides(recurrenceMappingQueryService.listOverrideMappings(integrationId));
        recurrenceMappingQueryService.listRecurrenceEventMappings(integrationId)
                .forEach(recurrenceChangeService::deleteRecurrenceEvent);
    }

    private void deleteAllMappedEventData(Long integrationId) {
        deleteEventMappings(
                eventMappingQueryService.listEventMappings(integrationId)
        );
    }

    private void deleteOverrides(List<GoogleCalendarRecurrenceOverrideMapping> mappings) {
        if (mappings.isEmpty()) {
            return;
        }
        List<Long> overrideIds = mappings.stream()
                .map(GoogleCalendarRecurrenceOverrideMapping::getRecurrenceEventOverride)
                .map(override -> override.getOverrideId())
                .toList();
        recurrenceMappingCommandService.deleteOverrideMappings(mappings);
        recurrenceEventCommandService.deleteRecurrenceOverridesByIds(overrideIds);
    }

    private void deleteEventMappings(List<GoogleCalendarEventMapping> mappings) {
        if (mappings.isEmpty()) {
            return;
        }
        List<Long> eventIds = mappings.stream()
                .map(GoogleCalendarEventMapping::getEvent)
                .map(event -> event.getId())
                .toList();
        eventMappingCommandService.deleteEventMappings(mappings);
        eventCommandService.deleteEventsByIds(eventIds);
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
