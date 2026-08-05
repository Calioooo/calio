package com.calio.calendar.integration.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer.NormalizedEventSchedule;
import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.domain.GoogleCalendarMappingSyncStatus;
import com.calio.calendar.integration.domain.GoogleCalendarSyncTarget;
import com.calio.calendar.integration.domain.GoogleCalendarItemSnapshot;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceOverrideMappingRepository;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.ActiveRecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.CancelledRecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.EventCancellation;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.EventUpsert;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.NormalizedItem;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceEventCancellation;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceEventUpsert;
import com.calio.calendar.integration.service.GoogleCalendarMappingLockService.LockedMappingIndex;
import com.calio.calendar.integration.service.GoogleCalendarMappingLockService.RecurrenceOverrideMappingKey;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.service.TagService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleCalendarEventPagePersistenceService {

    private final GoogleCalendarIntegrationRepository integrationRepository;
    private final GoogleCalendarEventMappingRepository eventMappingRepository;
    private final EventRepository eventRepository;
    private final AccountRepository accountRepository;
    private final TagService tagService;
    private final GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository;
    private final GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository;
    private final RecurrenceEventRepository recurrenceEventRepository;
    private final RecurrenceEventOverrideRepository overrideRepository;
    private final GoogleOperationJobPersistenceService operationJobPersistenceService;
    private final GoogleCalendarMappingLockService mappingLockService;
    private final GoogleCalendarChangeDetectionService changeDetectionService;

    public GoogleCalendarEventPagePersistenceService(
            GoogleCalendarIntegrationRepository integrationRepository,
            GoogleCalendarEventMappingRepository eventMappingRepository,
            EventRepository eventRepository,
            AccountRepository accountRepository,
            TagService tagService,
            GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository,
            GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository,
            RecurrenceEventRepository recurrenceEventRepository,
            RecurrenceEventOverrideRepository overrideRepository,
            GoogleOperationJobPersistenceService operationJobPersistenceService,
            GoogleCalendarChangeDetectionService changeDetectionService,
            GoogleCalendarMappingLockService mappingLockService
    ) {
        this.integrationRepository = integrationRepository;
        this.eventMappingRepository = eventMappingRepository;
        this.eventRepository = eventRepository;
        this.accountRepository = accountRepository;
        this.tagService = tagService;
        this.recurrenceMappingRepository = recurrenceMappingRepository;
        this.overrideMappingRepository = overrideMappingRepository;
        this.recurrenceEventRepository = recurrenceEventRepository;
        this.overrideRepository = overrideRepository;
        this.operationJobPersistenceService = operationJobPersistenceService;
        this.changeDetectionService = changeDetectionService;
        this.mappingLockService = mappingLockService;
    }

    @Transactional
    public void persistOwnedNormalizedPage(
            Long jobId,
            Long integrationId,
            Long accountId,
            String workerToken,
            GoogleCalendarNormalizedPage page
    ) {
        LockedMappingIndex lockedMappings = mappingLockService.lockMappingsForPage(
                integrationId, page.items()
        );
        operationJobPersistenceService.renewAndAssertOwned(jobId, accountId, workerToken);
        extendSyncLeaseOrThrow(integrationId, workerToken);
        GoogleCalendarIntegration integration = integrationRepository.getReferenceById(integrationId);
        applyEventChanges(integration, accountId, page.items(), lockedMappings,
                new SyncJobContext(jobId, accountId, workerToken));
    }

    private void applyEventChanges(
            GoogleCalendarIntegration integration,
            Long accountId,
            List<NormalizedItem> items,
            LockedMappingIndex lockedMappings,
            SyncJobContext syncJobContext
    ) {
        LoadedMappings loadedMappings =
                loadLoadedMappings(lockedMappings, items);
        boolean isLocalCreationNeeded = items.stream().anyMatch(item ->
                isLocalCreationNeeded(item, loadedMappings));
        Account account = isLocalCreationNeeded
                ? accountRepository.getReferenceById(accountId)
                : null;
        Tag defaultTag = isLocalCreationNeeded
                ? tagService.getTagOrDefault(accountId, null)
                : null;

        for (NormalizedItem item : items) {
            switch (item) {
                case EventUpsert event ->
                        upsertEvent(integration, accountId, event, loadedMappings,
                                account, defaultTag, syncJobContext);
                case EventCancellation cancellation ->
                        deleteEvent(integration.getId(), accountId,
                                cancellation.externalEventId(), loadedMappings,
                                syncJobContext);
                case RecurrenceEventUpsert recurrenceEvent -> upsertRecurrenceEvent(
                        integration,
                        recurrenceEvent,
                        loadedMappings,
                        account,
                        defaultTag,
                        accountId,
                        syncJobContext
                );
                case RecurrenceEventCancellation cancellation ->
                        deleteRecurrenceEventByExternalId(integration.getId(), accountId,
                                cancellation.externalEventId(), loadedMappings,
                                syncJobContext);
                case RecurrenceEventOverrideUpsert override ->
                        upsertRecurrenceEventOverride(integration.getId(), accountId,
                                override, loadedMappings, syncJobContext);
            }
        }
    }

    private LoadedMappings loadLoadedMappings(
            LockedMappingIndex lockedMappings,
            List<NormalizedItem> items
    ) {
        Map<RecurrenceEventOverrideKey, RecurrenceEventOverride> recurrenceEventOverrides =
                loadRecurrenceEventOverrides(lockedMappings.recurrenceEventMappings(), items);
        return new LoadedMappings(
                lockedMappings.eventMappings(),
                lockedMappings.recurrenceEventMappings(),
                lockedMappings.overrideMappings(),
                recurrenceEventOverrides
        );
    }

    private Map<RecurrenceEventOverrideKey, RecurrenceEventOverride>
            loadRecurrenceEventOverrides(
                    Map<String, GoogleCalendarRecurrenceEventMapping> recurrenceEventMappings,
                    List<NormalizedItem> items
            ) {
        Map<RecurrenceEventOverrideKey, RecurrenceEventOverride> recurrenceEventOverrides =
                new HashMap<>();
        Map<String, List<RecurrenceEventOverrideUpsert>> overridesByRecurrenceEvent =
                items.stream()
                        .filter(RecurrenceEventOverrideUpsert.class::isInstance)
                        .map(RecurrenceEventOverrideUpsert.class::cast)
                        .collect(Collectors.groupingBy(
                                RecurrenceEventOverrideUpsert::recurrenceEventExternalId
                        ));
        overridesByRecurrenceEvent.forEach((recurrenceEventExternalId, overrideItems) -> {
            GoogleCalendarRecurrenceEventMapping recurrenceEventMapping =
                    recurrenceEventMappings.get(recurrenceEventExternalId);
            if (recurrenceEventMapping == null) {
                return;
            }
            Set<Instant> originStartTimes = overrideItems.stream()
                    .map(RecurrenceEventOverrideUpsert::originStartAt)
                    .collect(Collectors.toSet());
            overrideRepository.findByRecurrenceEvent_IdAndOriginStartAtIn(
                    recurrenceEventMapping.getRecurrenceEvent().getId(),
                    originStartTimes
            ).forEach(recurrenceEventOverride -> recurrenceEventOverrides.put(
                    new RecurrenceEventOverrideKey(
                            recurrenceEventOverride.getRecurrenceId(),
                            recurrenceEventOverride.getOriginStartAt()
                    ),
                    recurrenceEventOverride
            ));
        });
        return recurrenceEventOverrides;
    }

    private boolean isLocalCreationNeeded(
            NormalizedItem item,
            LoadedMappings loadedMappings
    ) {
        if (item instanceof EventUpsert) {
            return !loadedMappings.eventMappings().containsKey(item.externalEventId());
        }
        if (item instanceof RecurrenceEventUpsert) {
            return !loadedMappings.recurrenceEventMappings()
                    .containsKey(item.externalEventId());
        }
        return false;
    }

    private void upsertEvent(
            GoogleCalendarIntegration integration,
            Long accountId,
            EventUpsert item,
            LoadedMappings loadedMappings,
            Account account,
            Tag defaultTag,
            SyncJobContext syncJobContext
    ) {
        if (loadedMappings.recurrenceEventMappings().containsKey(item.externalEventId())) {
            deleteRecurrenceEventByExternalId(integration.getId(), accountId,
                    item.externalEventId(), loadedMappings, syncJobContext);
            if (loadedMappings.recurrenceEventMappings()
                    .containsKey(item.externalEventId())) {
                return;
            }
        }
        GoogleCalendarEventMapping existingMapping =
                loadedMappings.eventMappings().get(item.externalEventId());
        if (existingMapping != null) {
            updateExistingEventFromGoogle(
                    integration.getId(),
                    accountId,
                    existingMapping,
                    item,
                    GoogleCalendarContentHasher.hashEvent(item),
                    syncJobContext
            );
            return;
        }
        Event event = eventRepository.save(new Event(
                item.title(),
                item.description(),
                item.schedule().startAt(),
                item.schedule().endAt(),
                item.schedule().allDay(),
                item.schedule().timeZone(),
                null,
                defaultTag,
                account
        ));
        GoogleCalendarEventMapping mapping = eventMappingRepository.save(
                new GoogleCalendarEventMapping(
                        integration,
                        event,
                        item.externalEventId(),
                        GoogleCalendarContentHasher.snapshotForEvent(item)
                )
        );
        loadedMappings.eventMappings().put(item.externalEventId(), mapping);
    }

    private void updateEvent(Event event, EventUpsert item) {
        event.replace(
                item.title(),
                item.description(),
                item.schedule().startAt(),
                item.schedule().endAt(),
                item.schedule().allDay(),
                item.schedule().timeZone()
        );
    }

    private void upsertRecurrenceEvent(
            GoogleCalendarIntegration integration,
            RecurrenceEventUpsert item,
            LoadedMappings loadedMappings,
            Account account,
            Tag defaultTag,
            Long accountId,
            SyncJobContext syncJobContext
    ) {
        if (loadedMappings.eventMappings().containsKey(item.externalEventId())) {
            deleteEvent(integration.getId(), accountId, item.externalEventId(),
                    loadedMappings, syncJobContext);
            if (loadedMappings.eventMappings().containsKey(item.externalEventId())) {
                return;
            }
        }
        RecurrenceSchedule schedule = toRecurrenceSchedule(item.schedule());
        GoogleCalendarRecurrenceEventMapping existingMapping =
                loadedMappings.recurrenceEventMappings().get(item.externalEventId());
        if (existingMapping != null) {
            updateExistingRecurrenceEventFromGoogle(integration.getId(), accountId,
                    existingMapping, item, schedule, syncJobContext);
            return;
        }
        RecurrenceEvent recurrenceEvent = recurrenceEventRepository.save(new RecurrenceEvent(
                item.title(),
                item.description(),
                schedule,
                item.recurrenceRules(),
                defaultTag,
                account
        ));
        GoogleCalendarRecurrenceEventMapping mapping = recurrenceMappingRepository.saveAndFlush(
                new GoogleCalendarRecurrenceEventMapping(
                        integration,
                        recurrenceEvent,
                        item.externalEventId(),
                        GoogleCalendarContentHasher.snapshotForRecurrenceEvent(item)
                )
        );
        loadedMappings.recurrenceEventMappings().put(item.externalEventId(), mapping);
    }

    private void upsertRecurrenceEventOverride(
            Long integrationId,
            Long accountId,
            RecurrenceEventOverrideUpsert item,
            LoadedMappings loadedMappings,
            SyncJobContext syncJobContext
    ) {
        GoogleCalendarRecurrenceEventMapping recurrenceEventMapping =
                loadedMappings.recurrenceEventMappings()
                        .get(item.recurrenceEventExternalId());
        if (recurrenceEventMapping == null) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
        }
        if (recurrenceEventMapping.getSyncStatus()
                == GoogleCalendarMappingSyncStatus.CONFLICTED) {
            return;
        }
        RecurrenceOverrideMappingKey googleOverrideKey =
                new RecurrenceOverrideMappingKey(
                        recurrenceEventMapping.getId(),
                        item.externalEventId()
                );
        RecurrenceEventOverrideKey recurrenceEventOverrideKey =
                new RecurrenceEventOverrideKey(
                        recurrenceEventMapping.getRecurrenceEvent().getId(),
                        item.originStartAt()
                );
        GoogleCalendarRecurrenceOverrideMapping googleOverrideMapping =
                loadedMappings.googleOverrideMappings().get(googleOverrideKey);
        RecurrenceEventOverride recurrenceEventOverride =
                loadedMappings.recurrenceEventOverrides().get(recurrenceEventOverrideKey);
        if (googleOverrideMapping != null) {
            validateMappedRecurrenceOverrideKey(
                    googleOverrideMapping,
                    recurrenceEventOverrideKey
            );
            recurrenceEventOverride = googleOverrideMapping.getRecurrenceEventOverride();
            if (!shouldApplyGoogleOverrideContent(integrationId, accountId,
                    googleOverrideMapping, recurrenceEventOverride, item,
                    syncJobContext)) {
                return;
            }
        }
        throwIfOverrideMappedToDifferentGoogleEvent(
                googleOverrideMapping,
                recurrenceEventOverride,
                item,
                loadedMappings
        );
        if (recurrenceEventOverride == null) {
            recurrenceEventOverride = createRecurrenceEventOverride(
                    recurrenceEventMapping.getRecurrenceEvent(),
                    item
            );
            overrideRepository.save(recurrenceEventOverride);
            loadedMappings.recurrenceEventOverrides().put(
                    recurrenceEventOverrideKey,
                    recurrenceEventOverride
            );
        } else {
            updateRecurrenceEventOverride(recurrenceEventOverride, item);
        }
        if (googleOverrideMapping == null) {
            googleOverrideMapping = overrideMappingRepository.save(
                    new GoogleCalendarRecurrenceOverrideMapping(
                            recurrenceEventMapping,
                            recurrenceEventOverride,
                            item.externalEventId(),
                            GoogleCalendarContentHasher
                                    .snapshotForRecurrenceOverride(item)
                    ));
            loadedMappings.googleOverrideMappings().put(
                    googleOverrideKey,
                    googleOverrideMapping
            );
        } else {
            googleOverrideMapping.updateGoogleSnapshot(GoogleCalendarContentHasher
                    .snapshotForRecurrenceOverride(item));
        }
    }

    private void validateMappedRecurrenceOverrideKey(
            GoogleCalendarRecurrenceOverrideMapping googleOverrideMapping,
            RecurrenceEventOverrideKey expectedOverrideKey
    ) {
        RecurrenceEventOverride mappedRecurrenceEventOverride =
                googleOverrideMapping.getRecurrenceEventOverride();
        RecurrenceEventOverrideKey mappedOverrideKey = new RecurrenceEventOverrideKey(
                mappedRecurrenceEventOverride.getRecurrenceId(),
                mappedRecurrenceEventOverride.getOriginStartAt()
        );
        if (!mappedOverrideKey.equals(expectedOverrideKey)) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
        }
    }

    private void throwIfOverrideMappedToDifferentGoogleEvent(
            GoogleCalendarRecurrenceOverrideMapping googleOverrideMapping,
            RecurrenceEventOverride recurrenceEventOverride,
            RecurrenceEventOverrideUpsert item,
            LoadedMappings loadedMappings
    ) {
        if (googleOverrideMapping != null
                || recurrenceEventOverride == null
                || recurrenceEventOverride.getOverrideId() == null) {
            return;
        }
        boolean mappedToDifferentGoogleEvent =
                loadedMappings.googleOverrideMappings().values().stream()
                        .anyMatch(mapping -> mapping.getRecurrenceEventOverride().getOverrideId()
                                .equals(recurrenceEventOverride.getOverrideId())
                                && !mapping.getExternalEventId().equals(item.externalEventId()));
        if (mappedToDifferentGoogleEvent) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
        }
    }

    private RecurrenceEventOverride createRecurrenceEventOverride(
            RecurrenceEvent recurrenceEvent,
            RecurrenceEventOverrideUpsert item
    ) {
        if (item instanceof ActiveRecurrenceEventOverrideUpsert active) {
            return RecurrenceEventOverride.active(
                    recurrenceEvent,
                    active.originStartAt(),
                    active.title(),
                    active.description(),
                    toOverrideSchedule(active.schedule())
            );
        }
        CancelledRecurrenceEventOverrideUpsert cancelled =
                (CancelledRecurrenceEventOverrideUpsert) item;
        return RecurrenceEventOverride.deleted(
                recurrenceEvent,
                cancelled.originStartAt(),
                getOverrideDeletedAt(cancelled)
        );
    }

    private void updateRecurrenceEventOverride(
            RecurrenceEventOverride recurrenceEventOverride,
            RecurrenceEventOverrideUpsert item
    ) {
        if (item instanceof ActiveRecurrenceEventOverrideUpsert active) {
            recurrenceEventOverride.activate(
                    active.title(),
                    active.description(),
                    toOverrideSchedule(active.schedule())
            );
            return;
        }
        recurrenceEventOverride.markDeleted(getOverrideDeletedAt(item));
    }

    private Instant getOverrideDeletedAt(RecurrenceEventOverrideUpsert item) {
        return item.googleUpdatedAt() == null
                ? item.originStartAt()
                : item.googleUpdatedAt();
    }

    private CanonicalSchedule toOverrideSchedule(NormalizedEventSchedule schedule) {
        return CanonicalSchedule.recurrenceOverride(
                schedule.startAt(),
                schedule.endAt(),
                schedule.allDay(),
                schedule.timeZone()
        );
    }

    private RecurrenceSchedule toRecurrenceSchedule(NormalizedEventSchedule schedule) {
        return RecurrenceSchedule.create(
                schedule.allDay(),
                schedule.startAt(),
                schedule.endAt(),
                schedule.timeZone()
        );
    }

    private void updateExistingEventFromGoogle(
            Long integrationId,
            Long accountId,
            GoogleCalendarEventMapping mapping,
            EventUpsert item,
            String currentGoogleContentHash,
            SyncJobContext syncJobContext
    ) {
        if (mapping.getSyncStatus() == GoogleCalendarMappingSyncStatus.CONFLICTED) {
            return;
        }
        GoogleCalendarChangeDetector.ChangeType changeType =
                changeDetectionService.detectUpdate(
                accountId,
                integrationId,
                GoogleCalendarSyncTarget.event(mapping.getEvent().getId()),
                mapping.getSyncedContentHash(),
                GoogleCalendarContentHasher.hashEvent(mapping.getEvent()),
                currentGoogleContentHash
        );
        if (changeType == GoogleCalendarChangeDetector.ChangeType.CALIO_AND_GOOGLE_CHANGED) {
            mapping.markConflicted();
            markSyncJobConflict(syncJobContext);
            return;
        }
        if (changeType == GoogleCalendarChangeDetector.ChangeType.GOOGLE_CHANGED) {
            updateEvent(mapping.getEvent(), item);
        }
        mapping.updateGoogleSnapshot(new GoogleCalendarItemSnapshot(
                item.googleEtag(), item.googleUpdatedAt(), currentGoogleContentHash));
    }

    private void updateExistingRecurrenceEventFromGoogle(
            Long integrationId,
            Long accountId,
            GoogleCalendarRecurrenceEventMapping mapping,
            RecurrenceEventUpsert item,
            RecurrenceSchedule schedule,
            SyncJobContext syncJobContext
    ) {
        if (mapping.getSyncStatus() == GoogleCalendarMappingSyncStatus.CONFLICTED) {
            return;
        }
        String currentGoogleContentHash =
                GoogleCalendarContentHasher.hashRecurrenceEvent(item);
        GoogleCalendarChangeDetector.ChangeType changeType =
                changeDetectionService.detectUpdate(
                accountId,
                integrationId,
                GoogleCalendarSyncTarget.recurrenceEvent(
                        mapping.getRecurrenceEvent().getId()),
                mapping.getSyncedContentHash(),
                GoogleCalendarContentHasher.hashRecurrenceEvent(mapping.getRecurrenceEvent()),
                currentGoogleContentHash
        );
        if (changeType == GoogleCalendarChangeDetector.ChangeType.CALIO_AND_GOOGLE_CHANGED) {
            mapping.markConflicted();
            markSyncJobConflict(syncJobContext);
            return;
        }
        if (changeType == GoogleCalendarChangeDetector.ChangeType.GOOGLE_CHANGED) {
            mapping.getRecurrenceEvent().updateProviderContent(item.title(),
                    item.description(), schedule, item.recurrenceRules());
        }
        mapping.updateGoogleSnapshot(new GoogleCalendarItemSnapshot(
                item.googleEtag(), item.googleUpdatedAt(), currentGoogleContentHash));
    }

    private boolean shouldApplyGoogleOverrideContent(
            Long integrationId,
            Long accountId,
            GoogleCalendarRecurrenceOverrideMapping mapping,
            RecurrenceEventOverride override,
            RecurrenceEventOverrideUpsert item,
            SyncJobContext syncJobContext
    ) {
        if (mapping.getSyncStatus() == GoogleCalendarMappingSyncStatus.CONFLICTED) {
            return false;
        }
        String recurrenceEventExternalId =
                mapping.getRecurrenceEventMapping().getExternalEventId();
        String currentGoogleContentHash =
                GoogleCalendarContentHasher.hashRecurrenceOverride(item);
        GoogleCalendarChangeDetector.ChangeType changeType =
                changeDetectionService.detectUpdate(
                accountId,
                integrationId,
                GoogleCalendarSyncTarget.recurrenceOverride(
                        mapping.getRecurrenceEventMapping().getRecurrenceEvent().getId(),
                        item.originStartAt()),
                mapping.getSyncedContentHash(),
                GoogleCalendarContentHasher.hashRecurrenceOverride(
                        recurrenceEventExternalId, override
                ),
                currentGoogleContentHash
        );
        if (changeType == GoogleCalendarChangeDetector.ChangeType.CALIO_AND_GOOGLE_CHANGED) {
            mapping.markConflicted();
            markSyncJobConflict(syncJobContext);
            return false;
        }
        mapping.updateGoogleSnapshot(new GoogleCalendarItemSnapshot(
                item.googleEtag(), item.googleUpdatedAt(), currentGoogleContentHash));
        return changeType == GoogleCalendarChangeDetector.ChangeType.GOOGLE_CHANGED;
    }

    private void markSyncJobConflict(SyncJobContext syncJobContext) {
        operationJobPersistenceService.markMappingConflict(
                syncJobContext.jobId(),
                syncJobContext.accountId(),
                syncJobContext.workerToken()
        );
    }

    private void deleteEvent(
            Long integrationId,
            Long accountId,
            String externalEventId,
            LoadedMappings loadedMappings,
            SyncJobContext syncJobContext
    ) {
        GoogleCalendarEventMapping mapping = loadedMappings.eventMappings().get(externalEventId);
        if (mapping == null || mapping.getSyncStatus()
                == GoogleCalendarMappingSyncStatus.CONFLICTED) {
            return;
        }
        GoogleCalendarSyncTarget syncTarget =
                GoogleCalendarSyncTarget.event(mapping.getEvent().getId());
        GoogleCalendarChangeDetector.ChangeType changeType =
                changeDetectionService.detectDeletion(
                        accountId,
                        integrationId,
                        syncTarget,
                        mapping.getSyncedContentHash(),
                        GoogleCalendarContentHasher.hashEvent(mapping.getEvent())
                );
        if (changeType == GoogleCalendarChangeDetector.ChangeType.CALIO_AND_GOOGLE_CHANGED) {
            mapping.markConflicted();
            markSyncJobConflict(syncJobContext);
            return;
        }
        deleteEvent(externalEventId, loadedMappings);
    }

    private void deleteRecurrenceEventByExternalId(
            Long integrationId,
            Long accountId,
            String externalEventId,
            LoadedMappings loadedMappings,
            SyncJobContext syncJobContext
    ) {
        GoogleCalendarRecurrenceEventMapping mapping =
                loadedMappings.recurrenceEventMappings().get(externalEventId);
        if (mapping == null || mapping.getSyncStatus()
                == GoogleCalendarMappingSyncStatus.CONFLICTED) {
            return;
        }
        GoogleCalendarSyncTarget.RecurrenceEvent syncTarget =
                GoogleCalendarSyncTarget.recurrenceEvent(
                        mapping.getRecurrenceEvent().getId());
        GoogleCalendarChangeDetector.ChangeType changeType =
                changeDetectionService.detectRecurrenceEventDeletion(
                        accountId,
                        integrationId,
                        syncTarget,
                        mapping.getSyncedContentHash(),
                        GoogleCalendarContentHasher.hashRecurrenceEvent(
                                mapping.getRecurrenceEvent())
                );
        if (changeType == GoogleCalendarChangeDetector.ChangeType.CALIO_AND_GOOGLE_CHANGED) {
            mapping.markConflicted();
            markSyncJobConflict(syncJobContext);
            return;
        }
        deleteRecurrenceEventByExternalId(externalEventId, loadedMappings);
    }

    private void deleteEvent(
            String externalEventId,
            LoadedMappings loadedMappings
    ) {
        GoogleCalendarEventMapping eventMapping =
                loadedMappings.eventMappings().remove(externalEventId);
        if (eventMapping == null) {
            return;
        }
        Event event = eventMapping.getEvent();
        eventMappingRepository.delete(eventMapping);
        eventMappingRepository.flush();
        eventRepository.delete(event);
    }

    private void deleteRecurrenceEventByExternalId(
            String externalEventId,
            LoadedMappings loadedMappings
    ) {
        GoogleCalendarRecurrenceEventMapping recurrenceEventMapping =
                loadedMappings.recurrenceEventMappings().remove(externalEventId);
        if (recurrenceEventMapping == null) {
            return;
        }
        deleteRecurrenceEvent(recurrenceEventMapping);
        loadedMappings.googleOverrideMappings().entrySet().removeIf(entry ->
                entry.getKey().recurrenceEventMappingId()
                        .equals(recurrenceEventMapping.getId()));
        loadedMappings.recurrenceEventOverrides().entrySet().removeIf(entry ->
                entry.getKey().recurrenceEventId()
                        .equals(recurrenceEventMapping.getRecurrenceEvent().getId()));
    }

    void deleteRecurrenceEvent(GoogleCalendarRecurrenceEventMapping recurrenceEventMapping) {
        List<GoogleCalendarRecurrenceOverrideMapping> overrideMappings =
                overrideMappingRepository
                        .findAllWithRecurrenceEventMappingAndRecurrenceEventOverrideByRecurrenceEventMappingIdsForUpdate(
                        Set.of(recurrenceEventMapping.getId())
                );
        if (!overrideMappings.isEmpty()) {
            overrideMappingRepository.deleteAll(overrideMappings);
            overrideMappingRepository.flush();
        }
        overrideRepository.deleteByRecurrenceEvent_Id(
                recurrenceEventMapping.getRecurrenceEvent().getId()
        );
        overrideRepository.flush();
        recurrenceMappingRepository.delete(recurrenceEventMapping);
        recurrenceMappingRepository.flush();
        List<Event> occurrences = eventRepository.findByRecurrenceIdAndAccount_IdOrderByStartAtAsc(
                recurrenceEventMapping.getRecurrenceEvent().getId(),
                recurrenceEventMapping.getRecurrenceEvent().getAccount().getId()
        );
        if (!occurrences.isEmpty()) {
            eventRepository.deleteAll(occurrences);
            eventRepository.flush();
        }
        recurrenceEventRepository.delete(recurrenceEventMapping.getRecurrenceEvent());
    }

    private void extendSyncLeaseOrThrow(Long integrationId, String runId) {
        if (integrationRepository.extendSyncLease(integrationId, runId) != 1) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_CONFLICT);
        }
    }

    private record LoadedMappings(
            Map<String, GoogleCalendarEventMapping> eventMappings,
            Map<String, GoogleCalendarRecurrenceEventMapping> recurrenceEventMappings,
            Map<RecurrenceOverrideMappingKey, GoogleCalendarRecurrenceOverrideMapping>
                    googleOverrideMappings,
            Map<RecurrenceEventOverrideKey, RecurrenceEventOverride> recurrenceEventOverrides
    ) {
    }

    private record RecurrenceEventOverrideKey(Long recurrenceEventId, Instant originStartAt) {
    }

    private record SyncJobContext(Long jobId, Long accountId, String workerToken) {
    }

}
