package com.calio.calendar.integration.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage.EventCancellation;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage.EventUpsert;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage.NormalizedItem;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage.RecurrenceEventCancellation;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage.RecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage.RecurrenceEventUpsert;
import com.calio.calendar.integration.service.dto.GoogleCalendarPageRecordCache;
import com.calio.calendar.integration.service.dto.GoogleCalendarPageRecordCache.GoogleCalendarRecurrenceOverrideKey;
import com.calio.calendar.integration.service.dto.GoogleCalendarPageRecordCache.RecurrenceEventOverrideKey;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
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
public class GoogleCalendarPageChangeService {

    private final GoogleCalendarIntegrationQueryService integrationQueryService;
    private final GoogleCalendarEventMappingQueryService eventMappingQueryService;
    private final GoogleCalendarEventChangeService eventChangeService;
    private final GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService;
    private final AccountQueryService accountQueryService;
    private final TagQueryService tagQueryService;
    private final RecurrenceEventOverrideRepository overrideRepository;
    private final GoogleCalendarRecurrenceChangeService recurrenceChangeService;

    public GoogleCalendarPageChangeService(
            GoogleCalendarIntegrationQueryService integrationQueryService,
            GoogleCalendarEventMappingQueryService eventMappingQueryService,
            GoogleCalendarEventChangeService eventChangeService,
            GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService,
            AccountQueryService accountQueryService,
            TagQueryService tagQueryService,
            RecurrenceEventOverrideRepository overrideRepository,
            GoogleCalendarRecurrenceChangeService recurrenceChangeService
    ) {
        this.integrationQueryService = integrationQueryService;
        this.eventMappingQueryService = eventMappingQueryService;
        this.eventChangeService = eventChangeService;
        this.recurrenceMappingQueryService = recurrenceMappingQueryService;
        this.accountQueryService = accountQueryService;
        this.tagQueryService = tagQueryService;
        this.overrideRepository = overrideRepository;
        this.recurrenceChangeService = recurrenceChangeService;
    }

    @Transactional
    public void applyNormalizedPage(
            Long integrationId,
            Long accountId,
            GoogleCalendarNormalizedPage page
    ) {
        GoogleCalendarIntegration integration = integrationQueryService.getIntegrationById(integrationId);
        applyPageChanges(integration, accountId, page.items());
    }

    private void applyPageChanges(
            GoogleCalendarIntegration integration,
            Long accountId,
            List<NormalizedItem> items
    ) {
        GoogleCalendarPageRecordCache cache =
                loadPageRecordCache(integration.getId(), items);

        Account account = accountQueryService.getAccount(accountId);
        Tag defaultTag = tagQueryService.getTagOrDefault(accountId, null);

        for (NormalizedItem item : items) {
            switch (item) {
                case EventUpsert event -> {
                    removeRecurrenceEventWithSameExternalId(event.externalEventId(), cache);
                    eventChangeService.applyUpsert(
                            integration,
                            event,
                            cache,
                            account,
                            defaultTag
                    );
                }
                case EventCancellation cancellation -> eventChangeService.applyCancellation(
                        cancellation.externalEventId(),
                        cache
                );
                case RecurrenceEventUpsert recurrenceEvent -> {
                    removeEventWithSameExternalId(recurrenceEvent.externalEventId(), cache);
                    recurrenceChangeService.applyUpsert(
                            integration,
                            recurrenceEvent,
                            cache,
                            account,
                            defaultTag
                    );
                }
                case RecurrenceEventCancellation cancellation -> recurrenceChangeService.applyCancellation(
                        cancellation.externalEventId(),
                        cache
                );
                case RecurrenceEventOverrideUpsert override -> recurrenceChangeService.applyRecurrenceEventOverride(
                        override,
                        cache
                );
            }
        }
    }

    private void removeRecurrenceEventWithSameExternalId(
            String externalEventId,
            GoogleCalendarPageRecordCache cache
    ) {
        if (!cache.recurrenceEventMappings().containsKey(externalEventId)) {
            return;
        }
        recurrenceChangeService.applyCancellation(externalEventId, cache);
    }

    private void removeEventWithSameExternalId(
            String externalEventId,
            GoogleCalendarPageRecordCache cache
    ) {
        if (!cache.eventMappings().containsKey(externalEventId)) {
            return;
        }
        eventChangeService.applyCancellation(externalEventId, cache);
    }

    private GoogleCalendarPageRecordCache loadPageRecordCache(
            Long integrationId,
            List<NormalizedItem> items
    ) {
        Set<String> eventExternalIds = items.stream()
                .map(NormalizedItem::externalEventId)
                .collect(Collectors.toSet());

        Set<String> recurrenceEventExternalIds = items.stream()
                .filter(RecurrenceEventOverrideUpsert.class::isInstance)
                .map(RecurrenceEventOverrideUpsert.class::cast)
                .map(RecurrenceEventOverrideUpsert::recurrenceEventExternalId)
                .collect(Collectors.toSet());

        recurrenceEventExternalIds.addAll(eventExternalIds);

        Map<String, GoogleCalendarEventMapping> eventMappings =
                loadEventMappings(integrationId, eventExternalIds);

        Map<String, GoogleCalendarRecurrenceEventMapping> recurrenceEventMappings =
                loadRecurrenceEventMappings(integrationId, recurrenceEventExternalIds);

        Map<GoogleCalendarRecurrenceOverrideKey, GoogleCalendarRecurrenceOverrideMapping>
                googleOverrideMappings = loadGoogleOverrideMappings(
                integrationId,
                recurrenceEventMappings,
                items
        );

        Map<RecurrenceEventOverrideKey, RecurrenceEventOverride> recurrenceEventOverrides =
                loadRecurrenceEventOverrides(recurrenceEventMappings, items);

        return new GoogleCalendarPageRecordCache(
                eventMappings,
                recurrenceEventMappings,
                googleOverrideMappings,
                recurrenceEventOverrides
        );
    }

    private Map<String, GoogleCalendarEventMapping> loadEventMappings(
            Long integrationId,
            Set<String> externalEventIds
    ) {
        if (externalEventIds.isEmpty()) {
            return new HashMap<>();
        }
        return eventMappingQueryService.listEventMappings(
                integrationId,
                GoogleCalendarEventMapping.PRIMARY_CALENDAR_KEY,
                externalEventIds
        ).stream().collect(Collectors.toMap(
                GoogleCalendarEventMapping::getExternalEventId,
                Function.identity(),
                (left, right) -> left,
                HashMap::new
        ));
    }

    private Map<String, GoogleCalendarRecurrenceEventMapping> loadRecurrenceEventMappings(
            Long integrationId,
            Set<String> recurrenceEventExternalIds
    ) {
        if (recurrenceEventExternalIds.isEmpty()) {
            return new HashMap<>();
        }
        return recurrenceMappingQueryService.listRecurrenceEventMappings(
                integrationId,
                GoogleCalendarRecurrenceEventMapping.PRIMARY_CALENDAR_KEY,
                recurrenceEventExternalIds
        ).stream().collect(Collectors.toMap(
                GoogleCalendarRecurrenceEventMapping::getExternalEventId,
                Function.identity(),
                (left, right) -> left,
                HashMap::new
        ));
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
}
