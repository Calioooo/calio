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
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceOverrideMappingRepository;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.GeneralCancellation;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.GeneralUpsert;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.NormalizedItem;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceMasterCancellation;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceMasterUpsert;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceOverrideUpsert;
import com.calio.calendar.integration.service.GoogleCalendarRecurrenceMapper.ActiveRecurrenceOverrideResult;
import com.calio.calendar.integration.service.GoogleCalendarRecurrenceMapper.CancelledRecurrenceOverrideResult;
import com.calio.calendar.integration.service.GoogleCalendarRecurrenceMapper.RecurrenceEventResult;
import com.calio.calendar.integration.service.GoogleCalendarRecurrenceMapper.RecurrenceOverrideResult;
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
import java.util.function.Function;
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

    public GoogleCalendarEventPagePersistenceService(
            GoogleCalendarIntegrationRepository integrationRepository,
            GoogleCalendarEventMappingRepository eventMappingRepository,
            EventRepository eventRepository,
            AccountRepository accountRepository,
            TagService tagService,
            GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository,
            GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository,
            RecurrenceEventRepository recurrenceEventRepository,
            RecurrenceEventOverrideRepository overrideRepository
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
    }

    @Transactional
    public void persistNormalizedPage(
            Long integrationId,
            Long accountId,
            String runId,
            GoogleCalendarNormalizedPage page
    ) {
        extendLeaseOrThrow(integrationId, runId);
        GoogleCalendarIntegration integration = integrationRepository.getReferenceById(integrationId);
        persistNormalizedItems(integration, accountId, page.items());
    }

    private void persistNormalizedItems(
            GoogleCalendarIntegration integration,
            Long accountId,
            List<NormalizedItem> items
    ) {
        PageMappings mappings = loadPageMappings(integration.getId(), items);
        boolean requiresCanonicalCreate = items.stream().anyMatch(item ->
                requiresCanonicalCreate(item, mappings));
        Account account = requiresCanonicalCreate
                ? accountRepository.getReferenceById(accountId)
                : null;
        Tag defaultTag = requiresCanonicalCreate
                ? tagService.getTagOrDefault(accountId, null)
                : null;

        for (NormalizedItem item : items) {
            switch (item) {
                case GeneralUpsert general ->
                        upsertGeneral(integration, general, mappings, account, defaultTag);
                case GeneralCancellation cancellation ->
                        deleteGeneral(cancellation.externalEventId(), mappings);
                case RecurrenceMasterUpsert master -> upsertMaster(
                        integration,
                        master.result(),
                        mappings,
                        account,
                        defaultTag
                );
                case RecurrenceMasterCancellation cancellation -> deleteMaster(
                        cancellation.externalEventId(),
                        mappings
                );
                case RecurrenceOverrideUpsert override ->
                        upsertOverride(override.result(), mappings);
            }
        }
    }

    private PageMappings loadPageMappings(Long integrationId, List<NormalizedItem> items) {
        Set<String> itemIds = items.stream()
                .map(NormalizedItem::externalEventId)
                .collect(Collectors.toSet());
        Set<String> masterIds = items.stream()
                .filter(RecurrenceOverrideUpsert.class::isInstance)
                .map(RecurrenceOverrideUpsert.class::cast)
                .map(item -> item.result().parentExternalEventId())
                .collect(Collectors.toSet());
        masterIds.addAll(itemIds);

        Map<String, GoogleCalendarEventMapping> generalMappings = itemIds.isEmpty()
                ? new HashMap<>()
                : eventMappingRepository.findAllByExternalIdentity(
                        integrationId,
                        GoogleCalendarEventMapping.PRIMARY_CALENDAR_KEY,
                        itemIds
                ).stream().collect(Collectors.toMap(
                        GoogleCalendarEventMapping::getExternalEventId,
                        Function.identity(),
                        (left, right) -> left,
                        HashMap::new
                ));
        Map<String, GoogleCalendarRecurrenceEventMapping> masterMappings = masterIds.isEmpty()
                ? new HashMap<>()
                : recurrenceMappingRepository.findAllByExternalIdentity(
                        integrationId,
                        GoogleCalendarRecurrenceEventMapping.PRIMARY_CALENDAR_KEY,
                        masterIds
                ).stream().collect(Collectors.toMap(
                        GoogleCalendarRecurrenceEventMapping::getExternalEventId,
                        Function.identity(),
                        (left, right) -> left,
                        HashMap::new
                ));
        Map<OverrideProviderKey, GoogleCalendarRecurrenceOverrideMapping> overrideMappings =
                loadOverrideMappings(masterMappings, items);
        Map<OverrideCanonicalKey, RecurrenceEventOverride> overrides =
                loadCanonicalOverrides(masterMappings, items);
        return new PageMappings(generalMappings, masterMappings, overrideMappings, overrides);
    }

    private Map<OverrideProviderKey, GoogleCalendarRecurrenceOverrideMapping> loadOverrideMappings(
            Map<String, GoogleCalendarRecurrenceEventMapping> masterMappings,
            List<NormalizedItem> items
    ) {
        Set<Long> parentMappingIds = masterMappings.values().stream()
                .map(GoogleCalendarRecurrenceEventMapping::getId)
                .collect(Collectors.toSet());
        boolean hasOverrides = items.stream().anyMatch(RecurrenceOverrideUpsert.class::isInstance);
        if (parentMappingIds.isEmpty() || !hasOverrides) {
            return new HashMap<>();
        }
        return overrideMappingRepository.findAllByParentMappingIds(parentMappingIds)
                .stream().collect(Collectors.toMap(
                        mapping -> new OverrideProviderKey(
                                mapping.getRecurrenceEventMapping().getId(),
                                mapping.getExternalEventId()
                        ),
                        Function.identity(),
                        (left, right) -> left,
                        HashMap::new
                ));
    }

    private Map<OverrideCanonicalKey, RecurrenceEventOverride> loadCanonicalOverrides(
            Map<String, GoogleCalendarRecurrenceEventMapping> masterMappings,
            List<NormalizedItem> items
    ) {
        Map<OverrideCanonicalKey, RecurrenceEventOverride> overrides = new HashMap<>();
        Map<String, List<RecurrenceOverrideResult>> byParent = items.stream()
                .filter(RecurrenceOverrideUpsert.class::isInstance)
                .map(RecurrenceOverrideUpsert.class::cast)
                .map(RecurrenceOverrideUpsert::result)
                .collect(Collectors.groupingBy(RecurrenceOverrideResult::parentExternalEventId));
        byParent.forEach((parentExternalId, results) -> {
            GoogleCalendarRecurrenceEventMapping parentMapping = masterMappings.get(parentExternalId);
            if (parentMapping == null) {
                return;
            }
            Set<Instant> origins = results.stream()
                    .map(RecurrenceOverrideResult::originStartAt)
                    .collect(Collectors.toSet());
            overrideRepository.findByRecurrenceEvent_IdAndOriginStartAtIn(
                    parentMapping.getRecurrenceEvent().getId(),
                    origins
            ).forEach(override -> overrides.put(
                    new OverrideCanonicalKey(
                            override.getRecurrenceId(),
                            override.getOriginStartAt()
                    ),
                    override
            ));
        });
        return overrides;
    }

    private boolean requiresCanonicalCreate(NormalizedItem item, PageMappings mappings) {
        if (item instanceof GeneralUpsert) {
            return !mappings.generalMappings().containsKey(item.externalEventId());
        }
        if (item instanceof RecurrenceMasterUpsert) {
            return !mappings.masterMappings().containsKey(item.externalEventId());
        }
        return false;
    }

    private void upsertGeneral(
            GoogleCalendarIntegration integration,
            GeneralUpsert item,
            PageMappings mappings,
            Account account,
            Tag defaultTag
    ) {
        if (mappings.masterMappings().containsKey(item.externalEventId())) {
            deleteMaster(item.externalEventId(), mappings);
        }
        GoogleCalendarEventMapping existing = mappings.generalMappings().get(item.externalEventId());
        if (existing != null) {
            replaceEvent(existing.getEvent(), item);
            existing.updateProviderVersion(item.providerEtag(), item.providerUpdatedAt());
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
                        item.providerEtag(),
                        item.providerUpdatedAt()
                )
        );
        mappings.generalMappings().put(item.externalEventId(), mapping);
    }

    private void replaceEvent(Event event, GeneralUpsert item) {
        event.replace(
                item.title(),
                item.description(),
                item.schedule().startAt(),
                item.schedule().endAt(),
                item.schedule().allDay(),
                item.schedule().timeZone()
        );
    }

    private void upsertMaster(
            GoogleCalendarIntegration integration,
            RecurrenceEventResult result,
            PageMappings mappings,
            Account account,
            Tag defaultTag
    ) {
        if (mappings.generalMappings().containsKey(result.externalEventId())) {
            deleteGeneral(result.externalEventId(), mappings);
        }
        RecurrenceSchedule schedule = recurrenceSchedule(result.schedule());
        GoogleCalendarRecurrenceEventMapping existing =
                mappings.masterMappings().get(result.externalEventId());
        if (existing != null) {
            existing.getRecurrenceEvent().updateProviderContent(
                    result.title(),
                    result.description(),
                    schedule,
                    result.recurrenceRules()
            );
            existing.updateProviderVersion(result.providerEtag(), result.providerUpdatedAt());
            return;
        }
        RecurrenceEvent recurrenceEvent = recurrenceEventRepository.save(new RecurrenceEvent(
                result.title(),
                result.description(),
                schedule,
                result.recurrenceRules(),
                defaultTag,
                account
        ));
        GoogleCalendarRecurrenceEventMapping mapping = recurrenceMappingRepository.saveAndFlush(
                new GoogleCalendarRecurrenceEventMapping(
                        integration,
                        recurrenceEvent,
                        result.externalEventId(),
                        result.providerEtag(),
                        result.providerUpdatedAt()
                )
        );
        mappings.masterMappings().put(result.externalEventId(), mapping);
    }

    private void upsertOverride(RecurrenceOverrideResult result, PageMappings mappings) {
        GoogleCalendarRecurrenceEventMapping parent =
                mappings.masterMappings().get(result.parentExternalEventId());
        if (parent == null) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
        }
        OverrideProviderKey providerKey = new OverrideProviderKey(
                parent.getId(),
                result.externalEventId()
        );
        OverrideCanonicalKey canonicalKey = new OverrideCanonicalKey(
                parent.getRecurrenceEvent().getId(),
                result.originStartAt()
        );
        GoogleCalendarRecurrenceOverrideMapping providerMapping =
                mappings.overrideMappings().get(providerKey);
        RecurrenceEventOverride override = mappings.overrides().get(canonicalKey);
        if (providerMapping != null) {
            override = reconcileProviderIdentity(providerMapping, canonicalKey, override, mappings);
        }
        rejectConflictingProviderIdentity(providerMapping, override, result, mappings);
        if (override == null) {
            override = createOverride(parent.getRecurrenceEvent(), result);
            overrideRepository.save(override);
            mappings.overrides().put(canonicalKey, override);
        } else {
            applyOverride(override, result);
        }
        if (providerMapping == null) {
            providerMapping = overrideMappingRepository.save(new GoogleCalendarRecurrenceOverrideMapping(
                    parent,
                    override,
                    result.externalEventId(),
                    result.providerEtag(),
                    result.providerUpdatedAt()
            ));
            mappings.overrideMappings().put(providerKey, providerMapping);
        } else {
            providerMapping.updateProviderVersion(result.providerEtag(), result.providerUpdatedAt());
        }
    }

    private RecurrenceEventOverride reconcileProviderIdentity(
            GoogleCalendarRecurrenceOverrideMapping providerMapping,
            OverrideCanonicalKey expectedIdentity,
            RecurrenceEventOverride exactOverride,
            PageMappings mappings
    ) {
        RecurrenceEventOverride mappedOverride = providerMapping.getRecurrenceEventOverride();
        OverrideCanonicalKey mappedIdentity = new OverrideCanonicalKey(
                mappedOverride.getRecurrenceId(),
                mappedOverride.getOriginStartAt()
        );
        if (mappedIdentity.equals(expectedIdentity)) {
            return mappedOverride;
        }
        throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
    }

    private void rejectConflictingProviderIdentity(
            GoogleCalendarRecurrenceOverrideMapping providerMapping,
            RecurrenceEventOverride override,
            RecurrenceOverrideResult result,
            PageMappings mappings
    ) {
        if (providerMapping != null || override == null || override.getOverrideId() == null) {
            return;
        }
        boolean hasDifferentMapping = mappings.overrideMappings().values().stream()
                .anyMatch(mapping -> mapping.getRecurrenceEventOverride().getOverrideId()
                        .equals(override.getOverrideId())
                        && !mapping.getExternalEventId().equals(result.externalEventId()));
        if (hasDifferentMapping) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
        }
    }

    private RecurrenceEventOverride createOverride(
            RecurrenceEvent recurrenceEvent,
            RecurrenceOverrideResult result
    ) {
        if (result instanceof ActiveRecurrenceOverrideResult active) {
            return RecurrenceEventOverride.active(
                    recurrenceEvent,
                    active.originStartAt(),
                    active.title(),
                    active.description(),
                    canonicalOverrideSchedule(active.schedule())
            );
        }
        CancelledRecurrenceOverrideResult cancelled =
                (CancelledRecurrenceOverrideResult) result;
        return RecurrenceEventOverride.deleted(
                recurrenceEvent,
                cancelled.originStartAt(),
                deletionInstant(cancelled)
        );
    }

    private void applyOverride(RecurrenceEventOverride override, RecurrenceOverrideResult result) {
        if (result instanceof ActiveRecurrenceOverrideResult active) {
            override.activate(
                    active.title(),
                    active.description(),
                    canonicalOverrideSchedule(active.schedule())
            );
            return;
        }
        override.markDeleted(deletionInstant(result));
    }

    private Instant deletionInstant(RecurrenceOverrideResult result) {
        return result.providerUpdatedAt() == null
                ? result.originStartAt()
                : result.providerUpdatedAt();
    }

    private CanonicalSchedule canonicalOverrideSchedule(NormalizedEventSchedule schedule) {
        return CanonicalSchedule.recurrenceOverride(
                schedule.startAt(),
                schedule.endAt(),
                schedule.allDay(),
                schedule.timeZone()
        );
    }

    private RecurrenceSchedule recurrenceSchedule(NormalizedEventSchedule schedule) {
        return RecurrenceSchedule.create(
                schedule.allDay(),
                schedule.startAt(),
                schedule.endAt(),
                schedule.timeZone()
        );
    }

    private void deleteGeneral(String externalEventId, PageMappings mappings) {
        GoogleCalendarEventMapping mapping = mappings.generalMappings().remove(externalEventId);
        if (mapping == null) {
            return;
        }
        Event event = mapping.getEvent();
        eventMappingRepository.delete(mapping);
        eventMappingRepository.flush();
        eventRepository.delete(event);
    }

    private void deleteMaster(String externalEventId, PageMappings mappings) {
        GoogleCalendarRecurrenceEventMapping mapping =
                mappings.masterMappings().remove(externalEventId);
        if (mapping == null) {
            return;
        }
        deleteMaster(mapping);
        mappings.overrideMappings().entrySet().removeIf(entry ->
                entry.getKey().parentMappingId().equals(mapping.getId()));
        mappings.overrides().entrySet().removeIf(entry ->
                entry.getKey().recurrenceEventId().equals(mapping.getRecurrenceEvent().getId()));
    }

    void deleteMaster(GoogleCalendarRecurrenceEventMapping mapping) {
        List<GoogleCalendarRecurrenceOverrideMapping> overrideMappings =
                overrideMappingRepository.findAllByParentMappingIds(Set.of(mapping.getId()));
        if (!overrideMappings.isEmpty()) {
            overrideMappingRepository.deleteAll(overrideMappings);
            overrideMappingRepository.flush();
        }
        overrideRepository.deleteByRecurrenceEvent_Id(mapping.getRecurrenceEvent().getId());
        overrideRepository.flush();
        recurrenceMappingRepository.delete(mapping);
        recurrenceMappingRepository.flush();
        List<Event> occurrences = eventRepository.findByRecurrenceIdAndAccount_IdOrderByStartAtAsc(
                mapping.getRecurrenceEvent().getId(),
                mapping.getRecurrenceEvent().getAccount().getId()
        );
        if (!occurrences.isEmpty()) {
            eventRepository.deleteAll(occurrences);
            eventRepository.flush();
        }
        recurrenceEventRepository.delete(mapping.getRecurrenceEvent());
    }

    private void extendLeaseOrThrow(Long integrationId, String runId) {
        if (integrationRepository.extendSyncLease(integrationId, runId) != 1) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_CONFLICT);
        }
    }

    private record PageMappings(
            Map<String, GoogleCalendarEventMapping> generalMappings,
            Map<String, GoogleCalendarRecurrenceEventMapping> masterMappings,
            Map<OverrideProviderKey, GoogleCalendarRecurrenceOverrideMapping> overrideMappings,
            Map<OverrideCanonicalKey, RecurrenceEventOverride> overrides
    ) {
    }

    private record OverrideProviderKey(Long parentMappingId, String externalEventId) {
    }

    private record OverrideCanonicalKey(Long recurrenceEventId, Instant originStartAt) {
    }
}
