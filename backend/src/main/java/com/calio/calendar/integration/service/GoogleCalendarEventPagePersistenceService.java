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
import com.calio.calendar.integration.domain.GoogleCalendarMappingStatus;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceOverrideMapping;
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
import com.calio.calendar.integration.service.GoogleCalendarConflictClassifier.Result;
import com.calio.calendar.integration.service.GoogleCalendarContentFingerprint.Fingerprint;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final GoogleCalendarContentFingerprint contentFingerprint;

    @Autowired
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
            GoogleCalendarContentFingerprint contentFingerprint
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
        this.contentFingerprint = contentFingerprint;
    }

    protected GoogleCalendarEventPagePersistenceService(
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
        this(
                integrationRepository,
                eventMappingRepository,
                eventRepository,
                accountRepository,
                tagService,
                recurrenceMappingRepository,
                overrideMappingRepository,
                recurrenceEventRepository,
                overrideRepository,
                new GoogleCalendarContentFingerprint()
        );
    }

    @Transactional
    public boolean persistNormalizedPage(
            Long integrationId,
            Long accountId,
            String runId,
            GoogleCalendarNormalizedPage page
    ) {
        extendSyncLeaseOrThrow(integrationId, runId);
        GoogleCalendarIntegration integration = integrationRepository.getReferenceById(integrationId);
        return applyEventChanges(integration, accountId, page.items());
    }

    private boolean applyEventChanges(
            GoogleCalendarIntegration integration,
            Long accountId,
            List<NormalizedItem> items
    ) {
        ExistingMappingsAndOverrides existingRecords =
                loadExistingMappingsAndOverrides(integration.getId(), items);
        boolean isLocalCreationNeeded = items.stream().anyMatch(item ->
                isLocalCreationNeeded(item, existingRecords));
        Account account = isLocalCreationNeeded
                ? accountRepository.getReferenceById(accountId)
                : null;
        Tag defaultTag = isLocalCreationNeeded
                ? tagService.getTagOrDefault(accountId, null)
                : null;

        boolean conflictDetected = false;
        for (NormalizedItem item : items) {
            boolean wasConflicted = isConflictedScope(item, existingRecords);
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
            conflictDetected |= !wasConflicted && isConflictedScope(item, existingRecords);
        }
        return conflictDetected;
    }

    private boolean isConflictedScope(
            NormalizedItem item,
            ExistingMappingsAndOverrides existingRecords
    ) {
        GoogleCalendarEventMapping eventMapping =
                existingRecords.eventMappings().get(item.externalEventId());
        if (eventMapping != null
                && eventMapping.getSyncStatus() == GoogleCalendarMappingStatus.CONFLICTED) {
            return true;
        }
        if (item instanceof RecurrenceEventOverrideUpsert override) {
            GoogleCalendarRecurrenceEventMapping master = existingRecords
                    .recurrenceEventMappings().get(override.recurrenceEventExternalId());
            if (master == null) {
                return false;
            }
            if (master.getSyncStatus() == GoogleCalendarMappingStatus.CONFLICTED) {
                return true;
            }
            GoogleCalendarRecurrenceOverrideMapping mapping = existingRecords
                    .googleOverrideMappings().get(new GoogleCalendarRecurrenceOverrideKey(
                            master.getId(), override.externalEventId()
                    ));
            return mapping != null
                    && mapping.getSyncStatus() == GoogleCalendarMappingStatus.CONFLICTED;
        }
        GoogleCalendarRecurrenceEventMapping recurrenceMapping = existingRecords
                .recurrenceEventMappings().get(item.externalEventId());
        return recurrenceMapping != null
                && recurrenceMapping.getSyncStatus() == GoogleCalendarMappingStatus.CONFLICTED;
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
                : eventMappingRepository.findAllWithEventByExternalIdentity(
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
                        : recurrenceMappingRepository
                                .findAllWithRecurrenceEventAndTagByExternalIdentity(
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
        return overrideMappingRepository
                .findAllWithRecurrenceEventMappingAndRecurrenceEventOverrideByRecurrenceEventMappingIds(
                        recurrenceEventMappingIds
                )
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
        overrideMappingRepository.findAllWithRecurrenceEventMappingByExternalEventIds(
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
        if (existingRecords.recurrenceEventMappings().containsKey(item.externalEventId())
                && !deleteRecurrenceEventByExternalId(item.externalEventId(), existingRecords)) {
            return;
        }
        eventMappingRepository.findByExternalIdentityForUpdate(
                integration.getId(),
                GoogleCalendarEventMapping.PRIMARY_CALENDAR_KEY,
                item.externalEventId()
        ).ifPresent(mapping -> existingRecords.eventMappings().put(
                item.externalEventId(), mapping
        ));
        GoogleCalendarEventMapping existingMapping =
                existingRecords.eventMappings().get(item.externalEventId());
        if (existingMapping != null) {
            reconcileEvent(existingMapping, item);
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
                        item.googleEtag(),
                        item.googleUpdatedAt()
                )
        );
        Fingerprint fingerprint = eventFingerprint(item);
        mapping.updateBaseline(
                item.googleEtag(), item.googleUpdatedAt(), fingerprint.version(), fingerprint.hash()
        );
        existingRecords.eventMappings().put(item.externalEventId(), mapping);
    }

    private void reconcileEvent(GoogleCalendarEventMapping mapping, EventUpsert item) {
        if (mapping.getSyncStatus() == GoogleCalendarMappingStatus.CONFLICTED
                || mapping.isLocalDeleted()) {
            return;
        }
        Fingerprint provider = eventFingerprint(item);
        Fingerprint local = eventFingerprint(mapping.getEvent());
        Result result = GoogleCalendarConflictClassifier.classify(
                mapping.getSyncedContentHash(), local.hash(), provider.hash()
        );
        if (result == Result.TRUE_CONFLICT) {
            mapping.updateProviderVersion(item.googleEtag(), item.googleUpdatedAt());
            mapping.markConflicted();
            return;
        }
        if (result == Result.CALIO_ONLY) {
            mapping.updateProviderVersion(item.googleEtag(), item.googleUpdatedAt());
            return;
        }
        if (result == Result.GOOGLE_ONLY) {
            updateEvent(mapping.getEvent(), item);
        }
        mapping.updateBaseline(
                item.googleEtag(), item.googleUpdatedAt(), provider.version(), provider.hash()
        );
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
        if (existingRecords.eventMappings().containsKey(item.externalEventId())
                && !deleteEvent(item.externalEventId(), existingRecords)) {
            return;
        }
        recurrenceMappingRepository.findByExternalIdentityForUpdate(
                integration.getId(),
                GoogleCalendarRecurrenceEventMapping.PRIMARY_CALENDAR_KEY,
                item.externalEventId()
        ).ifPresent(mapping -> existingRecords.recurrenceEventMappings().put(
                item.externalEventId(), mapping
        ));
        RecurrenceSchedule schedule = toRecurrenceSchedule(item.schedule());
        GoogleCalendarRecurrenceEventMapping existingMapping =
                existingRecords.recurrenceEventMappings().get(item.externalEventId());
        if (existingMapping != null) {
            reconcileRecurrenceEvent(existingMapping, item, schedule);
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
                        item.googleEtag(),
                        item.googleUpdatedAt()
                )
        );
        Fingerprint fingerprint = recurrenceFingerprint(item);
        mapping.updateBaseline(
                item.googleEtag(), item.googleUpdatedAt(), fingerprint.version(), fingerprint.hash()
        );
        existingRecords.recurrenceEventMappings().put(item.externalEventId(), mapping);
    }

    private void reconcileRecurrenceEvent(
            GoogleCalendarRecurrenceEventMapping mapping,
            RecurrenceEventUpsert item,
            RecurrenceSchedule schedule
    ) {
        if (mapping.getSyncStatus() == GoogleCalendarMappingStatus.CONFLICTED
                || mapping.isLocalDeleted()) {
            return;
        }
        Fingerprint provider = recurrenceFingerprint(item);
        Fingerprint local = recurrenceFingerprint(mapping.getRecurrenceEvent());
        Result result = GoogleCalendarConflictClassifier.classify(
                mapping.getSyncedContentHash(), local.hash(), provider.hash()
        );
        if (result == Result.TRUE_CONFLICT) {
            mapping.updateProviderVersion(item.googleEtag(), item.googleUpdatedAt());
            mapping.markConflicted();
            return;
        }
        if (result == Result.CALIO_ONLY) {
            mapping.updateProviderVersion(item.googleEtag(), item.googleUpdatedAt());
            return;
        }
        if (result == Result.GOOGLE_ONLY) {
            mapping.getRecurrenceEvent().updateProviderContent(
                    item.title(), item.description(), schedule, item.recurrenceRules()
            );
        }
        mapping.updateBaseline(
                item.googleEtag(), item.googleUpdatedAt(), provider.version(), provider.hash()
        );
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
        if (recurrenceEventMapping.getSyncStatus() == GoogleCalendarMappingStatus.CONFLICTED
                || recurrenceEventMapping.isLocalDeleted()) {
            return;
        }
        recurrenceEventMapping = recurrenceMappingRepository.findByExternalIdentityForUpdate(
                recurrenceEventMapping.getIntegration().getId(),
                GoogleCalendarRecurrenceEventMapping.PRIMARY_CALENDAR_KEY,
                item.recurrenceEventExternalId()
        ).orElseThrow(() -> new CalioException(
                ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID
        ));
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
        GoogleCalendarRecurrenceOverrideMapping lockedOverride = overrideMappingRepository
                .findExactScopeForUpdate(recurrenceEventMapping.getId(), item.originStartAt())
                .orElse(null);
        if (lockedOverride != null) {
            googleOverrideMapping = lockedOverride;
            existingRecords.googleOverrideMappings().put(googleOverrideKey, lockedOverride);
        }
        RecurrenceEventOverride recurrenceEventOverride =
                existingRecords.recurrenceEventOverrides().get(recurrenceEventOverrideKey);
        if (googleOverrideMapping != null) {
            validateMappedRecurrenceOverrideKey(
                    googleOverrideMapping,
                    recurrenceEventOverrideKey
            );
            recurrenceEventOverride = googleOverrideMapping.getRecurrenceEventOverride();
            if (googleOverrideMapping.getSyncStatus() == GoogleCalendarMappingStatus.CONFLICTED) {
                return;
            }
            Fingerprint provider = overrideFingerprint(item);
            Fingerprint local = overrideFingerprint(
                    item.recurrenceEventExternalId(),
                    recurrenceEventOverride
            );
            Result result = GoogleCalendarConflictClassifier.classify(
                    googleOverrideMapping.getSyncedContentHash(),
                    local.hash(),
                    provider.hash()
            );
            if (result == Result.TRUE_CONFLICT) {
                googleOverrideMapping.updateProviderVersion(
                        item.googleEtag(), item.googleUpdatedAt()
                );
                googleOverrideMapping.markConflicted();
                return;
            }
            if (result == Result.CALIO_ONLY) {
                googleOverrideMapping.updateProviderVersion(
                        item.googleEtag(), item.googleUpdatedAt()
                );
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
                            item.googleEtag(),
                            item.googleUpdatedAt()
                    ));
            existingRecords.googleOverrideMappings().put(
                    googleOverrideKey,
                    googleOverrideMapping
            );
        }
        Fingerprint provider = overrideFingerprint(item);
        googleOverrideMapping.updateBaseline(
                item.googleEtag(), item.googleUpdatedAt(), provider.version(), provider.hash()
        );
    }

    private Fingerprint eventFingerprint(EventUpsert item) {
        return contentFingerprint.generalEvent(
                item.title(), item.description(), item.schedule().startAt(),
                item.schedule().endAt(), item.schedule().allDay(), item.schedule().timeZone()
        );
    }

    private Fingerprint eventFingerprint(Event event) {
        return contentFingerprint.generalEvent(
                event.getTitle(), event.getDescription(), event.getStartAt(), event.getEndAt(),
                event.isAllDay(), event.getTimeZone()
        );
    }

    private Fingerprint recurrenceFingerprint(RecurrenceEventUpsert item) {
        return contentFingerprint.recurrenceMaster(
                item.title(), item.description(), item.schedule().startAt(),
                item.schedule().endAt(), item.schedule().allDay(), item.schedule().timeZone(),
                item.recurrenceRules()
        );
    }

    private Fingerprint recurrenceFingerprint(RecurrenceEvent event) {
        return contentFingerprint.recurrenceMaster(
                event.getRecurrenceTitle(), event.getRecurrenceDescription(),
                event.getFirstOccurrenceStartAt(), event.getFirstOccurrenceEndAt(),
                event.isAllDay(), event.getTimeZone(), event.getRecurrenceRules()
        );
    }

    private Fingerprint overrideFingerprint(RecurrenceEventOverrideUpsert item) {
        if (item instanceof CancelledRecurrenceEventOverrideUpsert) {
            return contentFingerprint.deletedOverride(
                    item.recurrenceEventExternalId(), item.originStartAt()
            );
        }
        ActiveRecurrenceEventOverrideUpsert active =
                (ActiveRecurrenceEventOverrideUpsert) item;
        return contentFingerprint.activeOverride(
                item.recurrenceEventExternalId(), item.originStartAt(), active.title(),
                active.description(), active.schedule().startAt(), active.schedule().endAt(),
                active.schedule().allDay(), active.schedule().timeZone()
        );
    }

    private Fingerprint overrideFingerprint(
            String recurrenceEventExternalId,
            RecurrenceEventOverride override
    ) {
        if (override.isDeleted()) {
            return contentFingerprint.deletedOverride(
                    recurrenceEventExternalId, override.getOriginStartAt()
            );
        }
        return contentFingerprint.activeOverride(
                recurrenceEventExternalId,
                override.getOriginStartAt(),
                override.getOverrideTitle(),
                override.getOverrideDescription(),
                override.getOverrideStartAt(),
                override.getOverrideEndAt(),
                override.isOverrideAllDay(),
                override.getOverrideTimeZone()
        );
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

    private boolean deleteEvent(
            String externalEventId,
            ExistingMappingsAndOverrides existingRecords
    ) {
        GoogleCalendarEventMapping eventMapping =
                existingRecords.eventMappings().get(externalEventId);
        if (eventMapping == null) {
            return true;
        }
        eventMapping = eventMappingRepository.findByExternalIdentityForUpdate(
                eventMapping.getIntegration().getId(),
                GoogleCalendarEventMapping.PRIMARY_CALENDAR_KEY,
                externalEventId
        ).orElse(null);
        if (eventMapping == null) {
            existingRecords.eventMappings().remove(externalEventId);
            return true;
        }
        existingRecords.eventMappings().put(externalEventId, eventMapping);
        if (eventMapping.getSyncStatus() == GoogleCalendarMappingStatus.CONFLICTED
                || eventMapping.isLocalDeleted()) {
            return false;
        }
        Fingerprint local = eventFingerprint(eventMapping.getEvent());
        if (eventMapping.getSyncedContentHash() != null
                && !eventMapping.getSyncedContentHash().equals(local.hash())) {
            eventMapping.markConflicted();
            return false;
        }
        Event event = eventMapping.getEvent();
        eventMappingRepository.delete(eventMapping);
        eventMappingRepository.flush();
        eventRepository.delete(event);
        existingRecords.eventMappings().remove(externalEventId);
        return true;
    }

    private boolean deleteRecurrenceEventByExternalId(
            String externalEventId,
            ExistingMappingsAndOverrides existingRecords
    ) {
        GoogleCalendarRecurrenceEventMapping recurrenceEventMapping =
                existingRecords.recurrenceEventMappings().get(externalEventId);
        if (recurrenceEventMapping == null) {
            return true;
        }
        GoogleCalendarRecurrenceEventMapping lockedMapping = recurrenceMappingRepository
                .findByExternalIdentityForUpdate(
                recurrenceEventMapping.getIntegration().getId(),
                GoogleCalendarRecurrenceEventMapping.PRIMARY_CALENDAR_KEY,
                externalEventId
        ).orElse(null);
        if (lockedMapping == null) {
            existingRecords.recurrenceEventMappings().remove(externalEventId);
            return true;
        }
        existingRecords.recurrenceEventMappings().put(externalEventId, lockedMapping);
        if (lockedMapping.getSyncStatus() == GoogleCalendarMappingStatus.CONFLICTED
                || lockedMapping.isLocalDeleted()) {
            return false;
        }
        Fingerprint local = recurrenceFingerprint(lockedMapping.getRecurrenceEvent());
        if (lockedMapping.getSyncedContentHash() != null
                && !lockedMapping.getSyncedContentHash().equals(local.hash())) {
            lockedMapping.markConflicted();
            return false;
        }
        Long lockedMappingId = lockedMapping.getId();
        Long lockedRecurrenceEventId = lockedMapping.getRecurrenceEvent().getId();
        deleteRecurrenceEvent(lockedMapping);
        existingRecords.recurrenceEventMappings().remove(externalEventId);
        existingRecords.googleOverrideMappings().entrySet().removeIf(entry ->
                entry.getKey().recurrenceEventMappingId()
                        .equals(lockedMappingId));
        existingRecords.recurrenceEventOverrides().entrySet().removeIf(entry ->
                entry.getKey().recurrenceEventId()
                        .equals(lockedRecurrenceEventId));
        return true;
    }

    void deleteRecurrenceEvent(GoogleCalendarRecurrenceEventMapping recurrenceEventMapping) {
        List<GoogleCalendarRecurrenceOverrideMapping> overrideMappings =
                overrideMappingRepository
                        .findAllWithRecurrenceEventMappingAndRecurrenceEventOverrideByRecurrenceEventMappingIds(
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
