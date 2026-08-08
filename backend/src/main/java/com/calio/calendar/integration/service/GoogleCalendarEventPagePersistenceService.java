package com.calio.calendar.integration.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.external.google.service.dto.NormalizedEventSchedule;
import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage.ActiveRecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage.CancelledRecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage.EventCancellation;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage.EventUpsert;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage.NormalizedItem;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage.RecurrenceEventCancellation;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage.RecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage.RecurrenceEventUpsert;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.service.TagQueryService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleCalendarEventPagePersistenceService {

    private final GoogleCalendarIntegrationQueryService integrationQueryService;
    private final GoogleCalendarIntegrationCommandService integrationCommandService;
    private final GoogleCalendarEventMappingQueryService eventMappingQueryService;
    private final GoogleCalendarEventMappingCommandService eventMappingCommandService;
    private final GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService;
    private final GoogleCalendarRecurrenceMappingCommandService recurrenceMappingCommandService;
    private final EventRepository eventRepository;
    private final AccountQueryService accountQueryService;
    private final TagQueryService tagQueryService;
    private final RecurrenceEventRepository recurrenceEventRepository;
    private final RecurrenceEventOverrideRepository overrideRepository;
    private final GoogleOperationJobPersistenceService operationJobPersistenceService;

    public GoogleCalendarEventPagePersistenceService(
            GoogleCalendarIntegrationQueryService integrationQueryService,
            GoogleCalendarIntegrationCommandService integrationCommandService,
            GoogleCalendarEventMappingQueryService eventMappingQueryService,
            GoogleCalendarEventMappingCommandService eventMappingCommandService,
            GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService,
            GoogleCalendarRecurrenceMappingCommandService recurrenceMappingCommandService,
            EventRepository eventRepository,
            AccountQueryService accountQueryService,
            TagQueryService tagQueryService,
            RecurrenceEventRepository recurrenceEventRepository,
            RecurrenceEventOverrideRepository overrideRepository,
            GoogleOperationJobPersistenceService operationJobPersistenceService
    ) {
        this.integrationQueryService = integrationQueryService;
        this.integrationCommandService = integrationCommandService;
        this.eventMappingQueryService = eventMappingQueryService;
        this.eventMappingCommandService = eventMappingCommandService;
        this.recurrenceMappingQueryService = recurrenceMappingQueryService;
        this.recurrenceMappingCommandService = recurrenceMappingCommandService;
        this.eventRepository = eventRepository;
        this.accountQueryService = accountQueryService;
        this.tagQueryService = tagQueryService;
        this.recurrenceEventRepository = recurrenceEventRepository;
        this.overrideRepository = overrideRepository;
        this.operationJobPersistenceService = operationJobPersistenceService;
    }

    @Transactional
    public void persistSyncPage(
            Long jobId,
            Long integrationId,
            Long accountId,
            String workerToken,
            GoogleCalendarNormalizedPage page
    ) {
        operationJobPersistenceService.renewAndAssertOwned(jobId, accountId, workerToken);
        extendSyncLeaseOrThrow(integrationId, workerToken);
        GoogleCalendarIntegration integration = integrationQueryService.getIntegrationById(integrationId);
        applyEventChanges(integration, accountId, page.items());
    }

    @Transactional
    public void persistNormalizedPage(
            Long integrationId,
            Long accountId,
            String runId,
            GoogleCalendarNormalizedPage page
    ) {
        extendSyncLeaseOrThrow(integrationId, runId);
        GoogleCalendarIntegration integration = integrationQueryService.getIntegrationById(integrationId);
        applyEventChanges(integration, accountId, page.items());
    }

    private void applyEventChanges(
            GoogleCalendarIntegration integration,
            Long accountId,
            List<NormalizedItem> items
    ) {
        ExistingMappingsAndOverrides existingRecords =
                loadExistingMappingsAndOverrides(integration.getId(), items);
        boolean isLocalCreationNeeded = items.stream().anyMatch(item ->
                isLocalCreationNeeded(item, existingRecords));
        Account account = isLocalCreationNeeded
                ? accountQueryService.getAccount(accountId)
                : null;
        Tag defaultTag = isLocalCreationNeeded
                ? tagQueryService.getTagOrDefault(accountId, null)
                : null;

        for (NormalizedItem item : items) {
            switch (item) {
                case EventUpsert event ->
                        upsertEvent(integration, event, existingRecords, account, defaultTag);
                case EventCancellation cancellation ->
                        deleteEvent(cancellation.externalEventId(), existingRecords);
                case RecurrenceEventUpsert recurrenceEvent -> upsertRecurrenceEvent(
                        integration,
                        recurrenceEvent,
                        existingRecords,
                        account,
                        defaultTag
                );
                case RecurrenceEventCancellation cancellation -> deleteRecurrenceEventByExternalId(
                        cancellation.externalEventId(),
                        existingRecords
                );
                case RecurrenceEventOverrideUpsert override ->
                        upsertRecurrenceEventOverride(override, existingRecords);
            }
        }
    }

    private ExistingMappingsAndOverrides loadExistingMappingsAndOverrides(
            Long integrationId,
            List<NormalizedItem> items
    ) {
        Set<String> externalEventIds = items.stream()
                .map(NormalizedItem::externalEventId)
                .collect(Collectors.toSet());
        Set<String> recurrenceEventExternalIds = items.stream()
                .filter(RecurrenceEventOverrideUpsert.class::isInstance)
                .map(RecurrenceEventOverrideUpsert.class::cast)
                .map(RecurrenceEventOverrideUpsert::recurrenceEventExternalId)
                .collect(Collectors.toSet());
        recurrenceEventExternalIds.addAll(externalEventIds);

        Map<String, GoogleCalendarEventMapping> eventMappings = externalEventIds.isEmpty()
                ? new HashMap<>()
                : eventMappingQueryService.listEventMappings(
                        integrationId,
                        GoogleCalendarEventMapping.PRIMARY_CALENDAR_KEY,
                        externalEventIds
                ).stream().collect(Collectors.toMap(
                        GoogleCalendarEventMapping::getExternalEventId,
                        Function.identity(),
                        (left, right) -> left,
                        HashMap::new
                ));
        Map<String, GoogleCalendarRecurrenceEventMapping> recurrenceEventMappings =
                recurrenceEventExternalIds.isEmpty()
                        ? new HashMap<>()
                        : recurrenceMappingQueryService.listRecurrenceEventMappings(
                                integrationId,
                                GoogleCalendarRecurrenceEventMapping.PRIMARY_CALENDAR_KEY,
                                recurrenceEventExternalIds
                        ).stream().collect(Collectors.toMap(
                                GoogleCalendarRecurrenceEventMapping::getExternalEventId,
                                Function.identity(),
                                (left, right) -> left,
                                HashMap::new
                        ));
        Map<GoogleCalendarRecurrenceOverrideKey, GoogleCalendarRecurrenceOverrideMapping>
                googleOverrideMappings = loadGoogleOverrideMappings(
                        integrationId,
                        recurrenceEventMappings,
                        items
                );
        Map<RecurrenceEventOverrideKey, RecurrenceEventOverride> recurrenceEventOverrides =
                loadRecurrenceEventOverrides(recurrenceEventMappings, items);
        return new ExistingMappingsAndOverrides(
                eventMappings,
                recurrenceEventMappings,
                googleOverrideMappings,
                recurrenceEventOverrides
        );
    }

    private Map<GoogleCalendarRecurrenceOverrideKey, GoogleCalendarRecurrenceOverrideMapping>
            loadGoogleOverrideMappings(
                    Long integrationId,
                    Map<String, GoogleCalendarRecurrenceEventMapping> recurrenceEventMappings,
                    List<NormalizedItem> items
            ) {
        Map<String, String> recurrenceEventExternalIdsByOverrideExternalId =
                recurrenceEventExternalIdsByOverrideExternalId(items);
        rejectOverrideExternalIdsUsedByDifferentRecurrenceEvent(
                integrationId,
                recurrenceEventExternalIdsByOverrideExternalId
        );
        Set<Long> recurrenceEventMappingIds = recurrenceEventMappings.values().stream()
                .map(GoogleCalendarRecurrenceEventMapping::getId)
                .collect(Collectors.toSet());
        if (recurrenceEventMappingIds.isEmpty()
                || recurrenceEventExternalIdsByOverrideExternalId.isEmpty()) {
            return new HashMap<>();
        }
        return recurrenceMappingQueryService.listOverrideMappings(recurrenceEventMappingIds)
                .stream().collect(Collectors.toMap(
                        mapping -> new GoogleCalendarRecurrenceOverrideKey(
                                mapping.getRecurrenceEventMapping().getId(),
                                mapping.getExternalEventId()
                        ),
                        Function.identity(),
                        (left, right) -> left,
                        HashMap::new
                ));
    }

    private Map<String, String> recurrenceEventExternalIdsByOverrideExternalId(
            List<NormalizedItem> items
    ) {
        Map<String, String> recurrenceEventExternalIdsByOverrideExternalId = new HashMap<>();
        items.stream()
                .filter(RecurrenceEventOverrideUpsert.class::isInstance)
                .map(RecurrenceEventOverrideUpsert.class::cast)
                .forEach(override -> {
                    String existingRecurrenceEventId =
                            recurrenceEventExternalIdsByOverrideExternalId.putIfAbsent(
                                    override.externalEventId(),
                                    override.recurrenceEventExternalId()
                            );
                    if (existingRecurrenceEventId != null
                            && !existingRecurrenceEventId.equals(
                                    override.recurrenceEventExternalId()
                            )) {
                        throw new CalioException(
                                ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID
                        );
                    }
                });
        return recurrenceEventExternalIdsByOverrideExternalId;
    }

    private void rejectOverrideExternalIdsUsedByDifferentRecurrenceEvent(
            Long integrationId,
            Map<String, String> recurrenceEventExternalIdsByOverrideExternalId
    ) {
        if (recurrenceEventExternalIdsByOverrideExternalId.isEmpty()) {
            return;
        }
        recurrenceMappingQueryService.listOverrideMappings(
                integrationId,
                GoogleCalendarRecurrenceEventMapping.PRIMARY_CALENDAR_KEY,
                recurrenceEventExternalIdsByOverrideExternalId.keySet()
        ).forEach(mapping -> {
            String expectedRecurrenceEventId =
                    recurrenceEventExternalIdsByOverrideExternalId.get(
                            mapping.getExternalEventId()
                    );
            String mappedRecurrenceEventId =
                    mapping.getRecurrenceEventMapping().getExternalEventId();
            if (!mappedRecurrenceEventId.equals(expectedRecurrenceEventId)) {
                throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
            }
        });
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
            EventUpsert item,
            ExistingMappingsAndOverrides existingRecords,
            Account account,
            Tag defaultTag
    ) {
        if (existingRecords.recurrenceEventMappings().containsKey(item.externalEventId())) {
            deleteRecurrenceEventByExternalId(item.externalEventId(), existingRecords);
        }
        GoogleCalendarEventMapping existingMapping =
                existingRecords.eventMappings().get(item.externalEventId());
        if (existingMapping != null) {
            updateEvent(existingMapping.getEvent(), item);
            existingMapping.updateProviderVersion(item.googleEtag(), item.googleUpdatedAt());
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
        GoogleCalendarEventMapping mapping = eventMappingCommandService.createEventMapping(
                new GoogleCalendarEventMapping(
                        integration,
                        event,
                        item.externalEventId(),
                        item.googleEtag(),
                        item.googleUpdatedAt()
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
            Tag defaultTag
    ) {
        if (existingRecords.eventMappings().containsKey(item.externalEventId())) {
            deleteEvent(item.externalEventId(), existingRecords);
        }
        RecurrenceSchedule schedule = toRecurrenceSchedule(item.schedule());
        GoogleCalendarRecurrenceEventMapping existingMapping =
                existingRecords.recurrenceEventMappings().get(item.externalEventId());
        if (existingMapping != null) {
            existingMapping.getRecurrenceEvent().updateProviderContent(
                    item.title(),
                    item.description(),
                    schedule,
                    item.recurrenceRules()
            );
            existingMapping.updateProviderVersion(item.googleEtag(), item.googleUpdatedAt());
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
        GoogleCalendarRecurrenceEventMapping mapping =
                recurrenceMappingCommandService.createRecurrenceEventMapping(
                new GoogleCalendarRecurrenceEventMapping(
                        integration,
                        recurrenceEvent,
                        item.externalEventId(),
                        item.googleEtag(),
                        item.googleUpdatedAt()
                )
        );
        existingRecords.recurrenceEventMappings().put(item.externalEventId(), mapping);
    }

    private void upsertRecurrenceEventOverride(
            RecurrenceEventOverrideUpsert item,
            ExistingMappingsAndOverrides existingRecords
    ) {
        GoogleCalendarRecurrenceEventMapping recurrenceEventMapping =
                existingRecords.recurrenceEventMappings()
                        .get(item.recurrenceEventExternalId());
        if (recurrenceEventMapping == null) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
        }
        GoogleCalendarRecurrenceOverrideKey googleOverrideKey =
                new GoogleCalendarRecurrenceOverrideKey(
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
            googleOverrideMapping = recurrenceMappingCommandService.createOverrideMapping(
                    new GoogleCalendarRecurrenceOverrideMapping(
                            recurrenceEventMapping,
                            recurrenceEventOverride,
                            item.externalEventId(),
                            item.googleEtag(),
                            item.googleUpdatedAt()
                    ));
            existingRecords.googleOverrideMappings().put(
                    googleOverrideKey,
                    googleOverrideMapping
            );
        } else {
            googleOverrideMapping.updateProviderVersion(
                    item.googleEtag(),
                    item.googleUpdatedAt()
            );
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
        eventMappingCommandService.deleteEventMapping(eventMapping);
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
                recurrenceMappingQueryService.listOverrideMappings(
                        Set.of(recurrenceEventMapping.getId())
                );
        if (!overrideMappings.isEmpty()) {
            recurrenceMappingCommandService.deleteOverrideMappings(overrideMappings);
        }
        overrideRepository.deleteByRecurrenceEvent_Id(
                recurrenceEventMapping.getRecurrenceEvent().getId()
        );
        overrideRepository.flush();
        recurrenceMappingCommandService.deleteRecurrenceEventMapping(recurrenceEventMapping);
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
        integrationCommandService.renewSyncLease(integrationId, runId);
    }

    private record ExistingMappingsAndOverrides(
            Map<String, GoogleCalendarEventMapping> eventMappings,
            Map<String, GoogleCalendarRecurrenceEventMapping> recurrenceEventMappings,
            Map<GoogleCalendarRecurrenceOverrideKey, GoogleCalendarRecurrenceOverrideMapping>
                    googleOverrideMappings,
            Map<RecurrenceEventOverrideKey, RecurrenceEventOverride> recurrenceEventOverrides
    ) {
    }

    private record GoogleCalendarRecurrenceOverrideKey(
            Long recurrenceEventMappingId,
            String overrideExternalEventId
    ) {
    }

    private record RecurrenceEventOverrideKey(Long recurrenceEventId, Instant originStartAt) {
    }
}
