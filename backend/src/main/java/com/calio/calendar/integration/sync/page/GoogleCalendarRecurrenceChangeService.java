package com.calio.calendar.integration.sync.page;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.service.EventCommandService;
import com.calio.calendar.external.google.service.dto.NormalizedEventSchedule;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingCommandService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobService;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEffectiveScope;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.ActiveRecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.CancelledRecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.RecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.RecurrenceEventUpsert;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarPageRecordCache;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarPageRecordCache.GoogleCalendarRecurrenceOverrideKey;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarPageRecordCache.RecurrenceEventOverrideKey;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.service.RecurrenceEventCommandService;
import com.calio.calendar.tag.domain.Tag;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarRecurrenceChangeService {

    private final GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService;
    private final GoogleCalendarRecurrenceMappingCommandService recurrenceMappingCommandService;
    private final EventCommandService eventCommandService;
    private final RecurrenceEventCommandService recurrenceEventCommandService;
    private final GoogleOperationJobQueryService operationJobQueryService;
    private final GoogleOperationJobService operationJobService;

    public GoogleCalendarRecurrenceChangeService(
            GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService,
            GoogleCalendarRecurrenceMappingCommandService recurrenceMappingCommandService,
            EventCommandService eventCommandService,
            RecurrenceEventCommandService recurrenceEventCommandService,
            GoogleOperationJobQueryService operationJobQueryService,
            GoogleOperationJobService operationJobService
    ) {
        this.recurrenceMappingQueryService = recurrenceMappingQueryService;
        this.recurrenceMappingCommandService = recurrenceMappingCommandService;
        this.eventCommandService = eventCommandService;
        this.recurrenceEventCommandService = recurrenceEventCommandService;
        this.operationJobQueryService = operationJobQueryService;
        this.operationJobService = operationJobService;
    }

    public void applyUpsert(
            GoogleCalendarConnection connection,
            RecurrenceEventUpsert item,
            GoogleCalendarPageRecordCache cache,
            Account account,
            Tag defaultTag,
            GoogleCalendarPageOwnership ownership
    ) {
        RecurrenceSchedule schedule = toRecurrenceSchedule(item.schedule());
        GoogleCalendarRecurrenceEventMapping existingMapping = cache.recurrenceEventMappings()
                .get(item.externalEventId());
        if (existingMapping != null) {
            applyExistingRecurrenceEventMapping(existingMapping, item, schedule, ownership);
            return;
        }
        RecurrenceEvent recurrenceEvent = recurrenceEventCommandService.createRecurrenceEvent(new RecurrenceEvent(
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
                                connection,
                                recurrenceEvent,
                                item.externalEventId(),
                                item.providerEtag()
                        )
                );
        cache.recurrenceEventMappings().put(item.externalEventId(), mapping);
    }

    private void applyExistingRecurrenceEventMapping(
            GoogleCalendarRecurrenceEventMapping mapping,
            RecurrenceEventUpsert item,
            RecurrenceSchedule schedule,
            GoogleCalendarPageOwnership ownership
    ) {
        if (mapping.isConflicted()) {
            return;
        }
        GoogleCalendarEffectiveScope scope = GoogleCalendarEffectiveScope.recurrenceEvent(
                mapping.getRecurrenceEvent().getId());
        if (mapping.getProviderEtag().equals(item.providerEtag())) {
            return;
        }
        if (operationJobQueryService.hasPendingOutboundJob(
                mapping.getConnection().getAccountId(), mapping.getConnection().getId(), scope)) {
            mapping.markConflicted();
            recordSyncConflict(mapping, ownership);
            return;
        }
        mapping.getRecurrenceEvent().updateProviderContent(
                item.title(), item.description(), schedule, item.recurrenceRules());
        mapping.updateProviderEtag(item.providerEtag());
    }

    public void applyCancellation(
            String externalEventId,
            GoogleCalendarPageRecordCache cache,
            GoogleCalendarPageOwnership ownership
    ) {
        GoogleCalendarRecurrenceEventMapping recurrenceEventMapping = cache.recurrenceEventMappings()
                .get(externalEventId);
        if (recurrenceEventMapping == null) {
            return;
        }
        if (recurrenceEventMapping.isConflicted()) {
            return;
        }
        GoogleCalendarEffectiveScope scope = GoogleCalendarEffectiveScope.recurrenceEvent(
                recurrenceEventMapping.getRecurrenceEvent().getId());
        if (operationJobQueryService.hasPendingOutboundJob(
                recurrenceEventMapping.getConnection().getAccountId(),
                recurrenceEventMapping.getConnection().getId(), scope)) {
            recurrenceEventMapping.markConflicted();
            recordSyncConflict(recurrenceEventMapping, ownership);
            return;
        }
        cache.recurrenceEventMappings().remove(externalEventId);
        deleteRecurrenceEvent(recurrenceEventMapping);
        cache.googleOverrideMappings().entrySet().removeIf(entry ->
                entry.getKey().recurrenceEventMappingId().equals(recurrenceEventMapping.getId()));
        cache.recurrenceEventOverrides().entrySet().removeIf(entry ->
                entry.getKey().recurrenceEventId()
                        .equals(recurrenceEventMapping.getRecurrenceEvent().getId()));
    }

    public void applyRecurrenceEventOverride(
            RecurrenceEventOverrideUpsert item,
            GoogleCalendarPageRecordCache cache,
            GoogleCalendarPageOwnership ownership
    ) {
        GoogleCalendarRecurrenceEventMapping recurrenceEventMapping =
                requireRecurrenceEventMapping(item, cache);
        if (recurrenceEventMapping.isConflicted()) {
            return;
        }
        ExistingRecurrenceOverride existingOverride = loadExistingOverride(
                recurrenceEventMapping,
                item,
                cache
        );
        if (existingOverride.googleOverrideMapping() != null) {
            applyExistingOverrideMapping(
                    recurrenceEventMapping,
                    existingOverride.googleOverrideMapping(),
                    item,
                    ownership
            );
            return;
        }
        RecurrenceEventOverride recurrenceEventOverride = applyOverride(
                recurrenceEventMapping,
                item,
                existingOverride,
                cache
        );
        applyOverrideMapping(
                recurrenceEventMapping,
                recurrenceEventOverride,
                item,
                existingOverride,
                cache
        );
    }

    private GoogleCalendarRecurrenceEventMapping requireRecurrenceEventMapping(
            RecurrenceEventOverrideUpsert item,
            GoogleCalendarPageRecordCache cache
    ) {
        GoogleCalendarRecurrenceEventMapping recurrenceEventMapping = cache.recurrenceEventMappings()
                .get(item.recurrenceEventExternalId());
        if (recurrenceEventMapping == null) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
        }
        return recurrenceEventMapping;
    }

    private ExistingRecurrenceOverride loadExistingOverride(
            GoogleCalendarRecurrenceEventMapping recurrenceEventMapping,
            RecurrenceEventOverrideUpsert item,
            GoogleCalendarPageRecordCache cache
    ) {
        GoogleCalendarRecurrenceOverrideKey googleOverrideKey =
                new GoogleCalendarRecurrenceOverrideKey(
                        recurrenceEventMapping.getId(),
                        item.externalEventId()
                );
        RecurrenceEventOverrideKey recurrenceEventOverrideKey = new RecurrenceEventOverrideKey(
                recurrenceEventMapping.getRecurrenceEvent().getId(),
                item.originStartAt()
        );
        GoogleCalendarRecurrenceOverrideMapping googleOverrideMapping = cache.googleOverrideMappings()
                .get(googleOverrideKey);
        RecurrenceEventOverride recurrenceEventOverride = cache.recurrenceEventOverrides()
                .get(recurrenceEventOverrideKey);
        if (googleOverrideMapping != null) {
            validateMappedRecurrenceOverrideKey(googleOverrideMapping, recurrenceEventOverrideKey);
            recurrenceEventOverride = googleOverrideMapping.getRecurrenceEventOverride();
        }
        throwIfOverrideMappedToDifferentGoogleEvent(
                googleOverrideMapping,
                recurrenceEventOverride,
                item,
                cache
        );
        return new ExistingRecurrenceOverride(
                googleOverrideKey,
                recurrenceEventOverrideKey,
                googleOverrideMapping,
                recurrenceEventOverride
        );
    }

    private RecurrenceEventOverride applyOverride(
            GoogleCalendarRecurrenceEventMapping recurrenceEventMapping,
            RecurrenceEventOverrideUpsert item,
            ExistingRecurrenceOverride existingOverride,
            GoogleCalendarPageRecordCache cache
    ) {
        if (existingOverride.recurrenceEventOverride() != null) {
            updateRecurrenceEventOverride(existingOverride.recurrenceEventOverride(), item);
            return existingOverride.recurrenceEventOverride();
        }
        RecurrenceEventOverride recurrenceEventOverride = createRecurrenceEventOverride(
                recurrenceEventMapping.getRecurrenceEvent(),
                item
        );
        recurrenceEventCommandService.createOrUpdateRecurrenceOverride(recurrenceEventOverride);
        cache.recurrenceEventOverrides().put(
                existingOverride.recurrenceEventOverrideKey(),
                recurrenceEventOverride
        );
        return recurrenceEventOverride;
    }

    private void applyOverrideMapping(
            GoogleCalendarRecurrenceEventMapping recurrenceEventMapping,
            RecurrenceEventOverride recurrenceEventOverride,
            RecurrenceEventOverrideUpsert item,
            ExistingRecurrenceOverride existingOverride,
            GoogleCalendarPageRecordCache cache
    ) {
        GoogleCalendarRecurrenceOverrideMapping googleOverrideMapping =
                recurrenceMappingCommandService.createOverrideMapping(
                        new GoogleCalendarRecurrenceOverrideMapping(
                                recurrenceEventMapping,
                                recurrenceEventOverride,
                                item.externalEventId(),
                                item.providerEtag()
                        )
                );
        cache.googleOverrideMappings().put(
                existingOverride.googleOverrideKey(),
                googleOverrideMapping
        );
    }

    private void applyExistingOverrideMapping(
            GoogleCalendarRecurrenceEventMapping recurrenceEventMapping,
            GoogleCalendarRecurrenceOverrideMapping mapping,
            RecurrenceEventOverrideUpsert item,
            GoogleCalendarPageOwnership ownership
    ) {
        if (mapping.isConflicted()) {
            return;
        }
        GoogleCalendarEffectiveScope scope = GoogleCalendarEffectiveScope.recurrenceOverride(
                recurrenceEventMapping.getRecurrenceEvent().getId(), item.originStartAt());
        if (mapping.getProviderEtag().equals(item.providerEtag())) {
            return;
        }
        if (operationJobQueryService.hasPendingOutboundJob(
                recurrenceEventMapping.getConnection().getAccountId(),
                recurrenceEventMapping.getConnection().getId(), scope)) {
            mapping.markConflicted();
            recordSyncConflict(mapping, ownership);
            return;
        }
        updateRecurrenceEventOverride(mapping.getRecurrenceEventOverride(), item);
        mapping.updateProviderEtag(item.providerEtag());
    }

    public void deleteRecurrenceEvent(
            GoogleCalendarRecurrenceEventMapping recurrenceEventMapping
    ) {
        List<GoogleCalendarRecurrenceOverrideMapping> overrideMappings =
                recurrenceMappingQueryService.listOverrideMappings(
                        Set.of(recurrenceEventMapping.getId())
                );
        if (!overrideMappings.isEmpty()) {
            recurrenceMappingCommandService.deleteOverrideMappings(overrideMappings);
        }
        recurrenceMappingCommandService.deleteRecurrenceEventMapping(recurrenceEventMapping);
        Long recurrenceEventId = recurrenceEventMapping.getRecurrenceEvent().getId();
        recurrenceEventCommandService.deleteRecurrenceOverridesByRecurrenceEventIds(List.of(recurrenceEventId));
        eventCommandService.deleteEventsByRecurrenceEventIds(List.of(recurrenceEventId));
        recurrenceEventCommandService.deleteRecurrenceEventsByIds(List.of(recurrenceEventId));
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
            GoogleCalendarPageRecordCache cache
    ) {
        if (googleOverrideMapping != null
                || recurrenceEventOverride == null
                || recurrenceEventOverride.getOverrideId() == null) {
            return;
        }
        boolean mappedToDifferentGoogleEvent = cache.googleOverrideMappings().values().stream()
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
                cancelled.deletedAt()
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
        recurrenceEventOverride.markDeleted(
                ((CancelledRecurrenceEventOverrideUpsert) item).deletedAt());
    }

    private void recordSyncConflict(
            GoogleCalendarRecurrenceEventMapping mapping,
            GoogleCalendarPageOwnership ownership
    ) {
        operationJobService.recordSyncConflict(
                ownership.jobId(),
                mapping.getConnection().getAccountId(),
                ownership.workerToken()
        );
    }

    private void recordSyncConflict(
            GoogleCalendarRecurrenceOverrideMapping mapping,
            GoogleCalendarPageOwnership ownership
    ) {
        recordSyncConflict(mapping.getRecurrenceEventMapping(), ownership);
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

    private record ExistingRecurrenceOverride(
            GoogleCalendarRecurrenceOverrideKey googleOverrideKey,
            RecurrenceEventOverrideKey recurrenceEventOverrideKey,
            GoogleCalendarRecurrenceOverrideMapping googleOverrideMapping,
            RecurrenceEventOverride recurrenceEventOverride
    ) {
    }
}
