package com.calio.calendar.integration.sync.page;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.external.google.service.dto.NormalizedEventSchedule;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingCommandService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingQueryService;
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
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.tag.domain.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarRecurrenceChangeService {

    private final GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService;
    private final GoogleCalendarRecurrenceMappingCommandService recurrenceMappingCommandService;
    private final EventRepository eventRepository;
    private final RecurrenceEventRepository recurrenceEventRepository;
    private final RecurrenceEventOverrideRepository overrideRepository;

    public GoogleCalendarRecurrenceChangeService(
            GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService,
            GoogleCalendarRecurrenceMappingCommandService recurrenceMappingCommandService,
            EventRepository eventRepository,
            RecurrenceEventRepository recurrenceEventRepository,
            RecurrenceEventOverrideRepository overrideRepository
    ) {
        this.recurrenceMappingQueryService = recurrenceMappingQueryService;
        this.recurrenceMappingCommandService = recurrenceMappingCommandService;
        this.eventRepository = eventRepository;
        this.recurrenceEventRepository = recurrenceEventRepository;
        this.overrideRepository = overrideRepository;
    }

    public void applyUpsert(
            GoogleCalendarIntegration integration,
            RecurrenceEventUpsert item,
            GoogleCalendarPageRecordCache cache,
            Account account,
            Tag defaultTag
    ) {
        RecurrenceSchedule schedule = toRecurrenceSchedule(item.schedule());
        GoogleCalendarRecurrenceEventMapping existingMapping = cache.recurrenceEventMappings()
                .get(item.externalEventId());
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
        cache.recurrenceEventMappings().put(item.externalEventId(), mapping);
    }

    public void applyCancellation(
            String externalEventId,
            GoogleCalendarPageRecordCache cache
    ) {
        GoogleCalendarRecurrenceEventMapping recurrenceEventMapping = cache.recurrenceEventMappings()
                .remove(externalEventId);
        if (recurrenceEventMapping == null) {
            return;
        }
        deleteRecurrenceEvent(recurrenceEventMapping);
        cache.googleOverrideMappings().entrySet().removeIf(entry ->
                entry.getKey().recurrenceEventMappingId().equals(recurrenceEventMapping.getId()));
        cache.recurrenceEventOverrides().entrySet().removeIf(entry ->
                entry.getKey().recurrenceEventId()
                        .equals(recurrenceEventMapping.getRecurrenceEvent().getId()));
    }

    public void applyRecurrenceEventOverride(
            RecurrenceEventOverrideUpsert item,
            GoogleCalendarPageRecordCache cache
    ) {
        GoogleCalendarRecurrenceEventMapping recurrenceEventMapping =
                requireRecurrenceEventMapping(item, cache);
        ExistingRecurrenceOverride existingOverride = resolveExistingOverride(
                recurrenceEventMapping,
                item,
                cache
        );
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

    private ExistingRecurrenceOverride resolveExistingOverride(
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
        overrideRepository.save(recurrenceEventOverride);
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
        if (existingOverride.googleOverrideMapping() != null) {
            existingOverride.googleOverrideMapping().updateProviderVersion(
                    item.googleEtag(),
                    item.googleUpdatedAt()
            );
            return;
        }
        GoogleCalendarRecurrenceOverrideMapping googleOverrideMapping =
                recurrenceMappingCommandService.createOverrideMapping(
                        new GoogleCalendarRecurrenceOverrideMapping(
                                recurrenceEventMapping,
                                recurrenceEventOverride,
                                item.externalEventId(),
                                item.googleEtag(),
                                item.googleUpdatedAt()
                        )
                );
        cache.googleOverrideMappings().put(
                existingOverride.googleOverrideKey(),
                googleOverrideMapping
        );
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
                deletedAt(cancelled)
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
        recurrenceEventOverride.markDeleted(deletedAt(item));
    }

    private Instant deletedAt(RecurrenceEventOverrideUpsert item) {
        return item.googleUpdatedAt() == null ? item.originStartAt() : item.googleUpdatedAt();
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
