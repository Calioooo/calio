package com.calio.calendar.integration.sync.page;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.integration.connection.service.GoogleCalendarIntegrationQueryService;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.mapping.service.GoogleCalendarMappingLockService;
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
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleCalendarPageChangeService {

    private final GoogleCalendarIntegrationQueryService integrationQueryService;
    private final GoogleCalendarMappingLockService mappingLockService;
    private final GoogleCalendarEventChangeService eventChangeService;
    private final AccountQueryService accountQueryService;
    private final TagQueryService tagQueryService;
    private final RecurrenceEventQueryService recurrenceEventQueryService;
    private final GoogleCalendarRecurrenceChangeService recurrenceChangeService;
    private final GoogleOperationLeaseService operationLeaseService;

    @Autowired
    public GoogleCalendarPageChangeService(
            GoogleCalendarIntegrationQueryService integrationQueryService,
            GoogleCalendarMappingLockService mappingLockService,
            GoogleCalendarEventChangeService eventChangeService,
            AccountQueryService accountQueryService,
            TagQueryService tagQueryService,
            RecurrenceEventQueryService recurrenceEventQueryService,
            GoogleCalendarRecurrenceChangeService recurrenceChangeService,
            GoogleOperationLeaseService operationLeaseService
    ) {
        this.integrationQueryService = integrationQueryService;
        this.mappingLockService = mappingLockService;
        this.eventChangeService = eventChangeService;
        this.accountQueryService = accountQueryService;
        this.tagQueryService = tagQueryService;
        this.recurrenceEventQueryService = recurrenceEventQueryService;
        this.recurrenceChangeService = recurrenceChangeService;
        this.operationLeaseService = operationLeaseService;
    }

    /** Kept for test doubles built against the pre-fenced page boundary. */
    protected GoogleCalendarPageChangeService(
            GoogleCalendarIntegrationQueryService integrationQueryService,
            GoogleCalendarEventMappingQueryService ignoredEventMappingQueryService,
            GoogleCalendarEventChangeService eventChangeService,
            GoogleCalendarRecurrenceMappingQueryService ignoredRecurrenceMappingQueryService,
            AccountQueryService accountQueryService,
            TagQueryService tagQueryService,
            RecurrenceEventQueryService recurrenceEventQueryService,
            GoogleCalendarRecurrenceChangeService recurrenceChangeService
    ) {
        this.integrationQueryService = integrationQueryService;
        this.mappingLockService = null;
        this.eventChangeService = eventChangeService;
        this.accountQueryService = accountQueryService;
        this.tagQueryService = tagQueryService;
        this.recurrenceEventQueryService = recurrenceEventQueryService;
        this.recurrenceChangeService = recurrenceChangeService;
        this.operationLeaseService = null;
    }

    // Package-private only for page persistence tests that do not exercise job fencing.
    void applyNormalizedPage(
            Long integrationId,
            Long accountId,
            GoogleCalendarNormalizedPage page
    ) {
        applyNormalizedPage(
                integrationId,
                accountId,
                new GoogleCalendarPageOwnership(-1L, "legacy-page-call"),
                page
        );
    }

    @Transactional
    public void applyNormalizedPage(
            Long integrationId,
            Long accountId,
            GoogleCalendarPageOwnership ownership,
            GoogleCalendarNormalizedPage page
    ) {
        if (operationLeaseService != null) {
            operationLeaseService.extend(ownership.jobId(), accountId, ownership.workerToken());
        }
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
        GoogleCalendarMappingLockService.LockedMappingIndex lockedMappings =
                mappingLockService.lockPageMappings(integrationId, items);
        Map<String, GoogleCalendarEventMapping> eventMappings = new HashMap<>(
                lockedMappings.eventMappings());
        Map<String, GoogleCalendarRecurrenceEventMapping> recurrenceEventMappings = new HashMap<>(
                lockedMappings.recurrenceEventMappings());
        Map<GoogleCalendarRecurrenceOverrideKey, GoogleCalendarRecurrenceOverrideMapping>
                googleOverrideMappings = new HashMap<>();
        lockedMappings.overrideMappings().forEach((key, mapping) -> googleOverrideMappings.put(
                new GoogleCalendarRecurrenceOverrideKey(
                        key.recurrenceEventMappingId(), key.externalEventId()), mapping));

        Map<RecurrenceEventOverrideKey, RecurrenceEventOverride> recurrenceEventOverrides =
                loadRecurrenceEventOverrides(recurrenceEventMappings, items);

        return new GoogleCalendarPageRecordCache(
                eventMappings,
                recurrenceEventMappings,
                googleOverrideMappings,
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
