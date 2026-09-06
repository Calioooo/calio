package com.calio.calendar.integration.sync.page;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.service.EventCommandService;
import com.calio.calendar.event.service.EventQueryService;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingCommandService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobService;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEffectiveScope;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarPageRecordCache;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.EventUpsert;
import com.calio.calendar.tag.domain.Tag;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarEventChangeService {

    private final GoogleCalendarEventMappingCommandService eventMappingCommandService;
    private final GoogleCalendarEventMappingQueryService eventMappingQueryService;
    private final EventCommandService eventCommandService;
    private final EventQueryService eventQueryService;
    private final GoogleOperationJobQueryService operationJobQueryService;
    private final GoogleOperationJobService operationJobService;

    public GoogleCalendarEventChangeService(
            GoogleCalendarEventMappingCommandService eventMappingCommandService,
            GoogleCalendarEventMappingQueryService eventMappingQueryService,
            EventCommandService eventCommandService,
            EventQueryService eventQueryService,
            GoogleOperationJobQueryService operationJobQueryService,
            GoogleOperationJobService operationJobService
    ) {
        this.eventMappingCommandService = eventMappingCommandService;
        this.eventMappingQueryService = eventMappingQueryService;
        this.eventCommandService = eventCommandService;
        this.eventQueryService = eventQueryService;
        this.operationJobQueryService = operationJobQueryService;
        this.operationJobService = operationJobService;
    }

    public void applyUpsert(
            GoogleCalendarConnection connection,
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
                        connection,
                        event.getId(),
                        item.externalEventId(),
                        item.providerEtag()
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
        GoogleCalendarEffectiveScope scope = GoogleCalendarEffectiveScope.event(
                mapping.getEventId());
        if (mapping.getProviderEtag().equals(item.providerEtag())) {
            return;
        }
        if (operationJobQueryService.hasPendingOutboundJob(
                mapping.getConnection().getAccountId(),
                mapping.getConnection().getIntegration().getId(),
                scope
        )) {
            mapping.markConflicted();
            recordSyncConflict(mapping, ownership);
            return;
        }
        Event event = eventQueryService.getEventIfExists(
                mapping.getConnection().getAccountId(), mapping.getEventId()).orElse(null);
        if (event == null) {
            return;
        }
        event.replace(
                item.title(), item.description(), item.schedule().startAt(), item.schedule().endAt(),
                item.schedule().allDay(), item.schedule().timeZone());
        mapping.updateProviderEtag(item.providerEtag());
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
                eventMapping.getEventId());
        if (operationJobQueryService.hasPendingOutboundJob(
                eventMapping.getConnection().getAccountId(),
                eventMapping.getConnection().getIntegration().getId(),
                scope
        )) {
            eventMapping.markConflicted();
            recordSyncConflict(eventMapping, ownership);
            return;
        }
        eventMappings.remove(externalEventId);
        eventMappingCommandService.deleteEventMapping(eventMapping);
        if (!eventMappingQueryService.listEventIdsWithMappings(List.of(eventMapping.getEventId()))
                .isEmpty()) {
            return;
        }
        eventQueryService.getEventIfExists(
                        eventMapping.getConnection().getAccountId(),
                        eventMapping.getEventId())
                .ifPresent(eventCommandService::deleteEvent);
    }

    private void recordSyncConflict(
            GoogleCalendarEventMapping mapping,
            GoogleCalendarPageOwnership ownership
    ) {
        operationJobService.recordSyncConflict(
                ownership.jobId(),
                mapping.getConnection().getAccountId(),
                ownership.workerToken()
        );
    }
}
