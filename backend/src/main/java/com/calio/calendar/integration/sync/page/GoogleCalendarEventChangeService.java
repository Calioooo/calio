package com.calio.calendar.integration.sync.page;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.service.EventCommandService;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarContentHasher;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingCommandService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobCommandService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobQueryService;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEffectiveScope;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarPageRecordCache;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.EventUpsert;
import com.calio.calendar.tag.domain.Tag;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarEventChangeService {

    private final GoogleCalendarEventMappingCommandService eventMappingCommandService;
    private final EventCommandService eventCommandService;
    private final GoogleOperationJobQueryService operationJobQueryService;
    private final GoogleOperationJobCommandService operationJobCommandService;
    private final GoogleCalendarContentReconciliationPolicy reconciliationPolicy;

    public GoogleCalendarEventChangeService(
            GoogleCalendarEventMappingCommandService eventMappingCommandService,
            EventCommandService eventCommandService,
            GoogleOperationJobQueryService operationJobQueryService,
            GoogleOperationJobCommandService operationJobCommandService
    ) {
        this.eventMappingCommandService = eventMappingCommandService;
        this.eventCommandService = eventCommandService;
        this.operationJobQueryService = operationJobQueryService;
        this.operationJobCommandService = operationJobCommandService;
        this.reconciliationPolicy = new GoogleCalendarContentReconciliationPolicy();
    }

    public void applyUpsert(
            GoogleCalendarIntegration integration,
            EventUpsert item,
            GoogleCalendarPageRecordCache cache,
            Account account,
            Tag defaultTag,
            GoogleCalendarPageOwnership ownership
    ) {
        var eventMappings = cache.eventMappings();
        GoogleCalendarEventMapping existingMapping = eventMappings.get(item.externalEventId());
        if (existingMapping != null) {
            applyExistingMapping(existingMapping, item, ownership);
            return;
        }
        Event event = eventCommandService.createEvent(new Event(
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
                        GoogleCalendarContentHasher.hash(item)
                )
        );
        eventMappings.put(item.externalEventId(), mapping);
    }

    private void applyExistingMapping(
            GoogleCalendarEventMapping mapping,
            EventUpsert item,
            GoogleCalendarPageOwnership ownership
    ) {
        if (mapping.isConflicted()) {
            return;
        }
        GoogleCalendarEffectiveScope scope = GoogleCalendarEffectiveScope.event(mapping.getEvent().getId());
        GoogleCalendarContentReconciliationDecision decision = reconciliationPolicy.decide(
                mapping.getSyncedContentHash(),
                GoogleCalendarContentHasher.hash(mapping.getEvent()),
                operationJobQueryService.listPendingDesiredContentHashes(
                        mapping.getIntegration().getAccountId(), mapping.getIntegration().getId(), scope),
                GoogleCalendarContentHasher.hash(item)
        );
        if (decision == GoogleCalendarContentReconciliationDecision.TRUE_CONFLICT) {
            mapping.markConflicted();
            operationJobCommandService.markConflictDetected(ownership.jobId(), ownership.workerToken());
            return;
        }
        if (decision == GoogleCalendarContentReconciliationDecision.GOOGLE_ONLY) {
            mapping.getEvent().replace(
                    item.title(), item.description(), item.schedule().startAt(), item.schedule().endAt(),
                    item.schedule().allDay(), item.schedule().timeZone());
        }
        mapping.updateSyncedContentHash(GoogleCalendarContentHasher.hash(item));
    }

    public void applyCancellation(
            String externalEventId,
            GoogleCalendarPageRecordCache cache,
            GoogleCalendarPageOwnership ownership
    ) {
        var eventMappings = cache.eventMappings();
        GoogleCalendarEventMapping eventMapping = eventMappings.get(externalEventId);
        if (eventMapping == null) {
            return;
        }
        if (eventMapping.isConflicted()) {
            return;
        }
        GoogleCalendarEffectiveScope scope = GoogleCalendarEffectiveScope.event(
                eventMapping.getEvent().getId());
        if (!GoogleCalendarContentHasher.hash(eventMapping.getEvent())
                .equals(eventMapping.getSyncedContentHash())
                || !operationJobQueryService.listPendingDesiredContentHashes(
                        eventMapping.getIntegration().getAccountId(),
                        eventMapping.getIntegration().getId(), scope).isEmpty()) {
            eventMapping.markConflicted();
            operationJobCommandService.markConflictDetected(ownership.jobId(), ownership.workerToken());
            return;
        }
        eventMappings.remove(externalEventId);
        eventMappingCommandService.deleteEventMapping(eventMapping);
        eventCommandService.deleteEvent(eventMapping.getEvent());
    }
}
