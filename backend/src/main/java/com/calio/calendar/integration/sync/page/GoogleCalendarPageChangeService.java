package com.calio.calendar.integration.sync.page;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.connection.service.GoogleCalendarIntegrationQueryService;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingQueryService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationLeaseService;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.EventCancellation;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.EventUpsert;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.NormalizedItem;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.RecurrenceEventCancellation;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.RecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.RecurrenceEventUpsert;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarPageRecordCache;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarPageRecordCache.GoogleCalendarRecurrenceOverrideKey;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarPageRecordCache.RecurrenceEventOverrideKey;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.service.RecurrenceEventQueryService;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.service.TagQueryService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleCalendarPageChangeService {

    private final GoogleCalendarIntegrationQueryService integrationQueryService;
    private final GoogleCalendarEventMappingQueryService eventMappingQueryService;
    private final GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService;
    private final GoogleCalendarEventChangeService eventChangeService;
    private final AccountQueryService accountQueryService;
    private final TagQueryService tagQueryService;
    private final RecurrenceEventQueryService recurrenceEventQueryService;
    private final GoogleCalendarRecurrenceChangeService recurrenceChangeService;
    private final GoogleOperationLeaseService operationLeaseService;

    public GoogleCalendarPageChangeService(
            GoogleCalendarIntegrationQueryService integrationQueryService,
            GoogleCalendarEventMappingQueryService eventMappingQueryService,
            GoogleCalendarEventChangeService eventChangeService,
            GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService,
            AccountQueryService accountQueryService,
            TagQueryService tagQueryService,
            RecurrenceEventQueryService recurrenceEventQueryService,
            GoogleCalendarRecurrenceChangeService recurrenceChangeService,
            GoogleOperationLeaseService operationLeaseService
    ) {
        this.integrationQueryService = integrationQueryService;
        this.eventMappingQueryService = eventMappingQueryService;
        this.eventChangeService = eventChangeService;
        this.recurrenceMappingQueryService = recurrenceMappingQueryService;
        this.accountQueryService = accountQueryService;
        this.tagQueryService = tagQueryService;
        this.recurrenceEventQueryService = recurrenceEventQueryService;
        this.recurrenceChangeService = recurrenceChangeService;
        this.operationLeaseService = operationLeaseService;
    }

    @Transactional
    public void applyNormalizedPage(
            Long integrationId,
            Long accountId,
            GoogleCalendarPageOwnership ownership,
            GoogleCalendarNormalizedPage page
    ) {
        operationLeaseService.extend(ownership.jobId(), accountId, ownership.workerToken());
        GoogleCalendarIntegration integration = integrationQueryService.getIntegrationById(integrationId);
        applyPageChanges(integration, accountId, ownership, page.items());
    }

    private void applyPageChanges(
            GoogleCalendarIntegration integration,
            Long accountId,
            GoogleCalendarPageOwnership ownership,
            List<NormalizedItem> items
    ) {
        GoogleCalendarPageRecordCache cache =
                loadPageRecordCache(integration.getId(), items);

        Account account = accountQueryService.getAccount(accountId);
        Tag defaultTag = tagQueryService.getTagOrDefault(accountId, null);

        for (NormalizedItem item : items) {
            switch (item) {
                case EventUpsert event -> {
                    removeRecurrenceEventWithSameExternalId(event.externalEventId(), cache, ownership);
                    eventChangeService.applyUpsert(
                            integration,
                            event,
                            cache,
                            account,
                            defaultTag,
                            ownership
                    );
                }
                case EventCancellation cancellation -> eventChangeService.applyCancellation(
                        cancellation.externalEventId(),
                        cache,
                        ownership
                );
                case RecurrenceEventUpsert recurrenceEvent -> {
                    removeEventWithSameExternalId(recurrenceEvent.externalEventId(), cache, ownership);
                    recurrenceChangeService.applyUpsert(
                            integration,
                            recurrenceEvent,
                            cache,
                            account,
                            defaultTag,
                            ownership
                    );
                }
                case RecurrenceEventCancellation cancellation -> recurrenceChangeService.applyCancellation(
                        cancellation.externalEventId(),
                        cache,
                        ownership
                );
                case RecurrenceEventOverrideUpsert override -> recurrenceChangeService.applyRecurrenceEventOverride(
                        override,
                        cache,
                        ownership
                );
            }
        }
    }

    private void removeRecurrenceEventWithSameExternalId(
            String externalEventId,
            GoogleCalendarPageRecordCache cache,
            GoogleCalendarPageOwnership ownership
    ) {
        if (!cache.recurrenceEventMappings().containsKey(externalEventId)) {
            return;
        }
        recurrenceChangeService.applyCancellation(externalEventId, cache, ownership);
    }

    private void removeEventWithSameExternalId(
            String externalEventId,
            GoogleCalendarPageRecordCache cache,
            GoogleCalendarPageOwnership ownership
    ) {
        if (!cache.eventMappings().containsKey(externalEventId)) {
            return;
        }
        eventChangeService.applyCancellation(externalEventId, cache, ownership);
    }

    private GoogleCalendarPageRecordCache loadPageRecordCache(
            Long integrationId,
            List<NormalizedItem> items
    ) {
        Set<String> externalEventIds = items.stream()
                .map(NormalizedItem::externalEventId)
                .collect(Collectors.toCollection(TreeSet::new));
        Map<String, String> expectedParents = expectedParentsByOverrideExternalId(items);
        Set<String> recurrenceEventExternalIds = new TreeSet<>(externalEventIds);
        recurrenceEventExternalIds.addAll(expectedParents.values());

        Map<String, GoogleCalendarEventMapping> eventMappings = indexEventMappings(
                eventMappingQueryService.listEventMappings(
                        integrationId,
                        GoogleCalendarEventMapping.PRIMARY_CALENDAR_KEY,
                        externalEventIds
                )
        );
        Map<String, GoogleCalendarRecurrenceEventMapping> recurrenceEventMappings =
                indexRecurrenceEventMappings(
                        recurrenceMappingQueryService.listRecurrenceEventMappings(
                                integrationId,
                                GoogleCalendarRecurrenceEventMapping.PRIMARY_CALENDAR_KEY,
                                recurrenceEventExternalIds
                        )
                );
        Map<GoogleCalendarRecurrenceOverrideKey, GoogleCalendarRecurrenceOverrideMapping>
                googleOverrideMappings = indexOverrideMappings(
                        expectedParents,
                        expectedParents.isEmpty() ? List.of()
                                : recurrenceMappingQueryService.listOverrideMappings(
                                        integrationId,
                                        GoogleCalendarRecurrenceEventMapping.PRIMARY_CALENDAR_KEY,
                                        expectedParents.keySet()
                                )
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

    private Map<String, String> expectedParentsByOverrideExternalId(List<NormalizedItem> items) {
        Map<String, String> expectedParents = new HashMap<>();
        items.stream()
                .filter(RecurrenceEventOverrideUpsert.class::isInstance)
                .map(RecurrenceEventOverrideUpsert.class::cast)
                .forEach(override -> {
                    String previous = expectedParents.putIfAbsent(
                            override.externalEventId(), override.recurrenceEventExternalId());
                    if (previous != null && !previous.equals(override.recurrenceEventExternalId())) {
                        throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
                    }
                });
        return expectedParents;
    }

    private Map<String, GoogleCalendarEventMapping> indexEventMappings(
            List<GoogleCalendarEventMapping> mappings
    ) {
        Map<String, GoogleCalendarEventMapping> indexedMappings = new HashMap<>();
        mappings.forEach(mapping -> indexedMappings.put(mapping.getExternalEventId(), mapping));
        return indexedMappings;
    }

    private Map<String, GoogleCalendarRecurrenceEventMapping> indexRecurrenceEventMappings(
            List<GoogleCalendarRecurrenceEventMapping> mappings
    ) {
        Map<String, GoogleCalendarRecurrenceEventMapping> indexedMappings = new HashMap<>();
        mappings.forEach(mapping -> indexedMappings.put(mapping.getExternalEventId(), mapping));
        return indexedMappings;
    }

    private Map<GoogleCalendarRecurrenceOverrideKey, GoogleCalendarRecurrenceOverrideMapping>
    indexOverrideMappings(
            Map<String, String> expectedParents,
            List<GoogleCalendarRecurrenceOverrideMapping> mappings
    ) {
        Map<GoogleCalendarRecurrenceOverrideKey, GoogleCalendarRecurrenceOverrideMapping>
                indexedMappings = new HashMap<>();
        for (GoogleCalendarRecurrenceOverrideMapping mapping : mappings) {
            String expectedParent = expectedParents.get(mapping.getExternalEventId());
            if (!mapping.getRecurrenceEventMapping().getExternalEventId().equals(expectedParent)) {
                throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
            }
            indexedMappings.put(new GoogleCalendarRecurrenceOverrideKey(
                    mapping.getRecurrenceEventMapping().getId(), mapping.getExternalEventId()), mapping);
        }
        return indexedMappings;
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
            recurrenceEventQueryService.listOverrides(
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
