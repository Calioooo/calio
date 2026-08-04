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
import com.calio.calendar.integration.domain.GoogleCalendarEffectiveScope;
import com.calio.calendar.integration.domain.GoogleProviderObservation;
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
import com.calio.calendar.integration.service.GoogleCalendarMappingLockCoordinator.LockedMappings;
import com.calio.calendar.integration.service.GoogleCalendarMappingLockCoordinator.OverrideMappingKey;
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
    private final GoogleCalendarMappingLockCoordinator mappingLockCoordinator;
    private final GoogleCalendarInboundConflictService conflictService;

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
            GoogleCalendarInboundConflictService conflictService,
            GoogleCalendarMappingLockCoordinator mappingLockCoordinator
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
        this.conflictService = conflictService;
        this.mappingLockCoordinator = mappingLockCoordinator;
    }

    @Transactional
    public void persistOwnedNormalizedPage(
            Long jobId,
            Long integrationId,
            Long accountId,
            String workerToken,
            GoogleCalendarNormalizedPage page
    ) {
        LockedMappings lockedMappings = mappingLockCoordinator.lockPage(
                integrationId, page.items()
        );
        operationJobPersistenceService.renewAndAssertOwned(jobId, accountId, workerToken);
        extendSyncLeaseOrThrow(integrationId, workerToken);
        GoogleCalendarIntegration integration = integrationRepository.getReferenceById(integrationId);
        applyEventChanges(integration, accountId, page.items(), lockedMappings,
                new ConflictEvidence(jobId, accountId, workerToken));
    }

    private void applyEventChanges(
            GoogleCalendarIntegration integration,
            Long accountId,
            List<NormalizedItem> items,
            LockedMappings lockedMappings,
            ConflictEvidence conflictEvidence
    ) {
        ExistingMappingsAndOverrides existingRecords =
                loadExistingMappingsAndOverrides(lockedMappings, items);
        boolean isLocalCreationNeeded = items.stream().anyMatch(item ->
                isLocalCreationNeeded(item, existingRecords));
        Account account = isLocalCreationNeeded
                ? accountRepository.getReferenceById(accountId)
                : null;
        Tag defaultTag = isLocalCreationNeeded
                ? tagService.getTagOrDefault(accountId, null)
                : null;

        for (NormalizedItem item : items) {
            switch (item) {
                case EventUpsert event ->
                        upsertEvent(integration, accountId, event, existingRecords,
                                account, defaultTag, conflictEvidence);
                case EventCancellation cancellation ->
                        deleteEvent(integration.getId(), accountId,
                                cancellation.externalEventId(), existingRecords,
                                conflictEvidence);
                case RecurrenceEventUpsert recurrenceEvent -> upsertRecurrenceEvent(
                        integration,
                        recurrenceEvent,
                        existingRecords,
                        account,
                        defaultTag,
                        accountId,
                        conflictEvidence
                );
                case RecurrenceEventCancellation cancellation ->
                        deleteRecurrenceEventByExternalId(integration.getId(), accountId,
                                cancellation.externalEventId(), existingRecords,
                                conflictEvidence);
                case RecurrenceEventOverrideUpsert override ->
                        upsertRecurrenceEventOverride(integration.getId(), accountId,
                                override, existingRecords, conflictEvidence);
            }
        }
    }

    private ExistingMappingsAndOverrides loadExistingMappingsAndOverrides(
            LockedMappings lockedMappings,
            List<NormalizedItem> items
    ) {
        Map<RecurrenceEventOverrideKey, RecurrenceEventOverride> recurrenceEventOverrides =
                loadRecurrenceEventOverrides(lockedMappings.recurrenceEventMappings(), items);
        return new ExistingMappingsAndOverrides(
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
            ExistingMappingsAndOverrides existingRecords
    ) {
        if (item instanceof EventUpsert) {
            return !existingRecords.eventMappings().containsKey(item.externalEventId());
        }
        if (item instanceof RecurrenceEventUpsert) {
            return !existingRecords.recurrenceEventMappings()
                    .containsKey(item.externalEventId());
        }
        return false;
    }

    private void upsertEvent(
            GoogleCalendarIntegration integration,
            Long accountId,
            EventUpsert item,
            ExistingMappingsAndOverrides existingRecords,
            Account account,
            Tag defaultTag,
            ConflictEvidence conflictEvidence
    ) {
        if (existingRecords.recurrenceEventMappings().containsKey(item.externalEventId())) {
            deleteRecurrenceEventByExternalId(integration.getId(), accountId,
                    item.externalEventId(), existingRecords, conflictEvidence);
            if (existingRecords.recurrenceEventMappings()
                    .containsKey(item.externalEventId())) {
                return;
            }
        }
        GoogleCalendarEventMapping existingMapping =
                existingRecords.eventMappings().get(item.externalEventId());
        if (existingMapping != null) {
            String googleHash = GoogleProviderContentProjector.event(item);
            if (!applyEventClassification(integration.getId(), accountId, existingMapping,
                    item, googleHash, conflictEvidence)) {
                return;
            }
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
                        GoogleProviderContentProjector.eventObservation(item)
                )
        );
        existingRecords.eventMappings().put(item.externalEventId(), mapping);
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
            ExistingMappingsAndOverrides existingRecords,
            Account account,
            Tag defaultTag,
            Long accountId,
            ConflictEvidence conflictEvidence
    ) {
        if (existingRecords.eventMappings().containsKey(item.externalEventId())) {
            deleteEvent(integration.getId(), accountId, item.externalEventId(),
                    existingRecords, conflictEvidence);
            if (existingRecords.eventMappings().containsKey(item.externalEventId())) {
                return;
            }
        }
        RecurrenceSchedule schedule = toRecurrenceSchedule(item.schedule());
        GoogleCalendarRecurrenceEventMapping existingMapping =
                existingRecords.recurrenceEventMappings().get(item.externalEventId());
        if (existingMapping != null) {
            applyRecurrenceMasterClassification(integration.getId(), accountId,
                    existingMapping, item, schedule, conflictEvidence);
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
                        GoogleProviderContentProjector.recurrenceEventObservation(item)
                )
        );
        existingRecords.recurrenceEventMappings().put(item.externalEventId(), mapping);
    }

    private void upsertRecurrenceEventOverride(
            Long integrationId,
            Long accountId,
            RecurrenceEventOverrideUpsert item,
            ExistingMappingsAndOverrides existingRecords,
            ConflictEvidence conflictEvidence
    ) {
        GoogleCalendarRecurrenceEventMapping recurrenceEventMapping =
                existingRecords.recurrenceEventMappings()
                        .get(item.recurrenceEventExternalId());
        if (recurrenceEventMapping == null) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
        }
        if (recurrenceEventMapping.getSyncStatus()
                == GoogleCalendarMappingSyncStatus.CONFLICTED) {
            return;
        }
        OverrideMappingKey googleOverrideKey =
                new OverrideMappingKey(
                        recurrenceEventMapping.getId(),
                        item.externalEventId()
                );
        RecurrenceEventOverrideKey recurrenceEventOverrideKey =
                new RecurrenceEventOverrideKey(
                        recurrenceEventMapping.getRecurrenceEvent().getId(),
                        item.originStartAt()
                );
        GoogleCalendarRecurrenceOverrideMapping googleOverrideMapping =
                existingRecords.googleOverrideMappings().get(googleOverrideKey);
        RecurrenceEventOverride recurrenceEventOverride =
                existingRecords.recurrenceEventOverrides().get(recurrenceEventOverrideKey);
        if (googleOverrideMapping != null) {
            validateMappedRecurrenceOverrideKey(
                    googleOverrideMapping,
                    recurrenceEventOverrideKey
            );
            recurrenceEventOverride = googleOverrideMapping.getRecurrenceEventOverride();
            if (!applyOverrideClassification(integrationId, accountId,
                    googleOverrideMapping, recurrenceEventOverride, item,
                    conflictEvidence)) {
                return;
            }
        }
        throwIfOverrideMappedToDifferentGoogleEvent(
                googleOverrideMapping,
                recurrenceEventOverride,
                item,
                existingRecords
        );
        if (recurrenceEventOverride == null) {
            recurrenceEventOverride = createRecurrenceEventOverride(
                    recurrenceEventMapping.getRecurrenceEvent(),
                    item
            );
            overrideRepository.save(recurrenceEventOverride);
            existingRecords.recurrenceEventOverrides().put(
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
                            GoogleProviderContentProjector
                                    .recurrenceEventOverrideObservation(item)
                    ));
            existingRecords.googleOverrideMappings().put(
                    googleOverrideKey,
                    googleOverrideMapping
            );
        } else {
            googleOverrideMapping.observeProvider(GoogleProviderContentProjector
                    .recurrenceEventOverrideObservation(item));
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
            ExistingMappingsAndOverrides existingRecords
    ) {
        if (googleOverrideMapping != null
                || recurrenceEventOverride == null
                || recurrenceEventOverride.getOverrideId() == null) {
            return;
        }
        boolean mappedToDifferentGoogleEvent =
                existingRecords.googleOverrideMappings().values().stream()
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

    private boolean applyEventClassification(
            Long integrationId,
            Long accountId,
            GoogleCalendarEventMapping mapping,
            EventUpsert item,
            String googleHash,
            ConflictEvidence evidence
    ) {
        if (mapping.getSyncStatus() == GoogleCalendarMappingSyncStatus.CONFLICTED) {
            return false;
        }
        GoogleInboundChangeClassifier.Classification classification = classify(
                accountId, integrationId,
                GoogleCalendarEffectiveScope.generalEvent(mapping.getEvent().getId()),
                mapping.getSyncedContentHash(),
                GoogleProviderContentProjector.event(mapping.getEvent()), googleHash);
        if (classification == GoogleInboundChangeClassifier.Classification.TRUE_CONFLICT) {
            mapping.markConflicted();
            recordConflict(evidence);
            return false;
        }
        if (classification == GoogleInboundChangeClassifier.Classification.GOOGLE_ONLY) {
            updateEvent(mapping.getEvent(), item);
        }
        mapping.observeProvider(new GoogleProviderObservation(
                item.googleEtag(), item.googleUpdatedAt(), googleHash));
        return classification == GoogleInboundChangeClassifier.Classification.GOOGLE_ONLY;
    }

    private void applyRecurrenceMasterClassification(
            Long integrationId,
            Long accountId,
            GoogleCalendarRecurrenceEventMapping mapping,
            RecurrenceEventUpsert item,
            RecurrenceSchedule schedule,
            ConflictEvidence evidence
    ) {
        if (mapping.getSyncStatus() == GoogleCalendarMappingSyncStatus.CONFLICTED) {
            return;
        }
        String googleHash = GoogleProviderContentProjector.recurrenceMaster(item);
        GoogleInboundChangeClassifier.Classification classification = classify(
                accountId, integrationId,
                GoogleCalendarEffectiveScope.recurrenceMaster(
                        mapping.getRecurrenceEvent().getId()),
                mapping.getSyncedContentHash(),
                GoogleProviderContentProjector.recurrenceMaster(mapping.getRecurrenceEvent()),
                googleHash);
        if (classification == GoogleInboundChangeClassifier.Classification.TRUE_CONFLICT) {
            mapping.markConflicted();
            recordConflict(evidence);
            return;
        }
        if (classification == GoogleInboundChangeClassifier.Classification.GOOGLE_ONLY) {
            mapping.getRecurrenceEvent().updateProviderContent(item.title(),
                    item.description(), schedule, item.recurrenceRules());
        }
        mapping.observeProvider(new GoogleProviderObservation(
                item.googleEtag(), item.googleUpdatedAt(), googleHash));
    }

    private boolean applyOverrideClassification(
            Long integrationId,
            Long accountId,
            GoogleCalendarRecurrenceOverrideMapping mapping,
            RecurrenceEventOverride override,
            RecurrenceEventOverrideUpsert item,
            ConflictEvidence evidence
    ) {
        if (mapping.getSyncStatus() == GoogleCalendarMappingSyncStatus.CONFLICTED) {
            return false;
        }
        String masterId = mapping.getRecurrenceEventMapping().getExternalEventId();
        String googleHash = GoogleProviderContentProjector.recurrenceOverride(item);
        GoogleInboundChangeClassifier.Classification classification = classify(
                accountId, integrationId,
                GoogleCalendarEffectiveScope.recurrenceOverride(
                        mapping.getRecurrenceEventMapping().getRecurrenceEvent().getId(),
                        item.originStartAt()),
                mapping.getSyncedContentHash(),
                GoogleProviderContentProjector.recurrenceOverride(masterId, override),
                googleHash);
        if (classification == GoogleInboundChangeClassifier.Classification.TRUE_CONFLICT) {
            mapping.markConflicted();
            recordConflict(evidence);
            return false;
        }
        mapping.observeProvider(new GoogleProviderObservation(
                item.googleEtag(), item.googleUpdatedAt(), googleHash));
        return classification == GoogleInboundChangeClassifier.Classification.GOOGLE_ONLY;
    }

    private GoogleInboundChangeClassifier.Classification classify(
            Long accountId,
            Long integrationId,
            GoogleCalendarEffectiveScope scope,
            String baselineHash,
            String canonicalHash,
            String googleHash
    ) {
        return conflictService.classifyObservation(
                accountId,
                integrationId,
                scope,
                baselineHash,
                canonicalHash,
                googleHash
        );
    }

    private void recordConflict(ConflictEvidence evidence) {
        operationJobPersistenceService.markConflictDetected(
                evidence.jobId(), evidence.accountId(), evidence.workerToken()
        );
    }

    private void deleteEvent(
            Long integrationId,
            Long accountId,
            String externalEventId,
            ExistingMappingsAndOverrides existingRecords,
            ConflictEvidence evidence
    ) {
        GoogleCalendarEventMapping mapping = existingRecords.eventMappings().get(externalEventId);
        if (mapping == null || mapping.getSyncStatus()
                == GoogleCalendarMappingSyncStatus.CONFLICTED) {
            return;
        }
        GoogleCalendarEffectiveScope scope =
                GoogleCalendarEffectiveScope.generalEvent(mapping.getEvent().getId());
        GoogleInboundChangeClassifier.Classification classification =
                conflictService.classifyDeletion(
                        accountId,
                        integrationId,
                        scope,
                        mapping.getSyncedContentHash(),
                        GoogleProviderContentProjector.event(mapping.getEvent())
                );
        if (classification == GoogleInboundChangeClassifier.Classification.TRUE_CONFLICT) {
            mapping.markConflicted();
            recordConflict(evidence);
            return;
        }
        deleteEvent(externalEventId, existingRecords);
    }

    private void deleteRecurrenceEventByExternalId(
            Long integrationId,
            Long accountId,
            String externalEventId,
            ExistingMappingsAndOverrides existingRecords,
            ConflictEvidence evidence
    ) {
        GoogleCalendarRecurrenceEventMapping mapping =
                existingRecords.recurrenceEventMappings().get(externalEventId);
        if (mapping == null || mapping.getSyncStatus()
                == GoogleCalendarMappingSyncStatus.CONFLICTED) {
            return;
        }
        GoogleCalendarEffectiveScope.RecurrenceMaster scope =
                GoogleCalendarEffectiveScope.recurrenceMaster(
                        mapping.getRecurrenceEvent().getId());
        GoogleInboundChangeClassifier.Classification classification =
                conflictService.classifyRecurrenceEventDeletion(
                        accountId,
                        integrationId,
                        scope,
                        mapping.getSyncedContentHash(),
                        GoogleProviderContentProjector.recurrenceMaster(
                                mapping.getRecurrenceEvent())
                );
        if (classification == GoogleInboundChangeClassifier.Classification.TRUE_CONFLICT) {
            mapping.markConflicted();
            recordConflict(evidence);
            return;
        }
        deleteRecurrenceEventByExternalId(externalEventId, existingRecords);
    }

    private void deleteEvent(
            String externalEventId,
            ExistingMappingsAndOverrides existingRecords
    ) {
        GoogleCalendarEventMapping eventMapping =
                existingRecords.eventMappings().remove(externalEventId);
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
            ExistingMappingsAndOverrides existingRecords
    ) {
        GoogleCalendarRecurrenceEventMapping recurrenceEventMapping =
                existingRecords.recurrenceEventMappings().remove(externalEventId);
        if (recurrenceEventMapping == null) {
            return;
        }
        deleteRecurrenceEvent(recurrenceEventMapping);
        existingRecords.googleOverrideMappings().entrySet().removeIf(entry ->
                entry.getKey().recurrenceEventMappingId()
                        .equals(recurrenceEventMapping.getId()));
        existingRecords.recurrenceEventOverrides().entrySet().removeIf(entry ->
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

    private record ExistingMappingsAndOverrides(
            Map<String, GoogleCalendarEventMapping> eventMappings,
            Map<String, GoogleCalendarRecurrenceEventMapping> recurrenceEventMappings,
            Map<OverrideMappingKey, GoogleCalendarRecurrenceOverrideMapping>
                    googleOverrideMappings,
            Map<RecurrenceEventOverrideKey, RecurrenceEventOverride> recurrenceEventOverrides
    ) {
    }

    private record RecurrenceEventOverrideKey(Long recurrenceEventId, Instant originStartAt) {
    }

    private record ConflictEvidence(Long jobId, Long accountId, String workerToken) {
    }

}
