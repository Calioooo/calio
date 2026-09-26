package com.calio.calendar.integration.sync.page;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingCommandService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobService;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEffectiveScope;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.EventUpsert;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarPageRecordCache;
import com.calio.calendar.sharing.event.service.PersonalEventGroupShareCommandService;
import com.calio.calendar.singleevent.domain.SingleEvent;
import com.calio.calendar.singleevent.repository.SingleEventRepository;
import com.calio.calendar.tag.domain.Tag;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarEventChangeService {

  private final GoogleCalendarEventMappingCommandService eventMappingCommandService;
  private final GoogleCalendarEventMappingQueryService eventMappingQueryService;
  private final SingleEventRepository singleEventRepository;
  private final PersonalEventGroupShareCommandService eventShareCommandService;
  private final GoogleOperationJobQueryService operationJobQueryService;
  private final GoogleOperationJobService operationJobService;

  public GoogleCalendarEventChangeService(
      GoogleCalendarEventMappingCommandService eventMappingCommandService,
      GoogleCalendarEventMappingQueryService eventMappingQueryService,
      SingleEventRepository singleEventRepository,
      PersonalEventGroupShareCommandService eventShareCommandService,
      GoogleOperationJobQueryService operationJobQueryService,
      GoogleOperationJobService operationJobService) {
    this.eventMappingCommandService = eventMappingCommandService;
    this.eventMappingQueryService = eventMappingQueryService;
    this.singleEventRepository = singleEventRepository;
    this.eventShareCommandService = eventShareCommandService;
    this.operationJobQueryService = operationJobQueryService;
    this.operationJobService = operationJobService;
  }

  public void applyUpsert(
      GoogleCalendarConnection connection,
      EventUpsert item,
      GoogleCalendarPageRecordCache cache,
      Account account,
      Tag defaultTag,
      GoogleCalendarPageOwnership ownership) {
    var eventMappings = cache.eventMappings();
    GoogleCalendarEventMapping existingMapping = eventMappings.get(item.externalEventId());
    if (existingMapping != null) {
      applyExistingMapping(existingMapping, item, ownership);
      return;
    }
    SingleEvent event =
        singleEventRepository.save(
            new SingleEvent(
                item.title(),
                item.description(),
                item.schedule().startAt(),
                item.schedule().endAt(),
                item.schedule().allDay(),
                item.schedule().timeZone(),
                defaultTag.getId(),
                account.getId()));
    GoogleCalendarEventMapping mapping =
        eventMappingCommandService.createEventMapping(
            new GoogleCalendarEventMapping(
                connection, event.getId(), item.externalEventId(), item.providerEtag()));
    eventMappings.put(item.externalEventId(), mapping);
  }

  private void applyExistingMapping(
      GoogleCalendarEventMapping mapping, EventUpsert item, GoogleCalendarPageOwnership ownership) {
    if (mapping.isConflicted()) {
      return;
    }
    GoogleCalendarEffectiveScope scope = GoogleCalendarEffectiveScope.event(mapping.getEventId());
    if (mapping.getProviderEtag().equals(item.providerEtag())) {
      return;
    }
    if (operationJobQueryService.hasPendingOutboundJob(
        mapping.getConnection().getAccountId(),
        mapping.getConnection().getIntegration().getId(),
        scope)) {
      mapping.markConflicted();
      recordSyncConflict(mapping, ownership);
      return;
    }
    SingleEvent event =
        singleEventRepository
            .findByIdAndAccountId(mapping.getEventId(), mapping.getConnection().getAccountId())
            .orElse(null);
    if (event == null) {
      return;
    }
    event.replace(
        item.title(),
        item.description(),
        item.schedule().startAt(),
        item.schedule().endAt(),
        item.schedule().allDay(),
        item.schedule().timeZone());
    mapping.updateProviderEtag(item.providerEtag());
  }

  public void applyCancellation(
      String externalEventId,
      GoogleCalendarPageRecordCache cache,
      GoogleCalendarPageOwnership ownership) {
    var eventMappings = cache.eventMappings();
    GoogleCalendarEventMapping eventMapping = eventMappings.get(externalEventId);
    if (eventMapping == null) {
      return;
    }
    if (eventMapping.isConflicted()) {
      return;
    }
    GoogleCalendarEffectiveScope scope =
        GoogleCalendarEffectiveScope.event(eventMapping.getEventId());
    if (operationJobQueryService.hasPendingOutboundJob(
        eventMapping.getConnection().getAccountId(),
        eventMapping.getConnection().getIntegration().getId(),
        scope)) {
      eventMapping.markConflicted();
      recordSyncConflict(eventMapping, ownership);
      return;
    }
    eventMappings.remove(externalEventId);
    eventMappingCommandService.deleteEventMapping(eventMapping);
    if (!eventMappingQueryService
        .listEventIdsWithMappings(List.of(eventMapping.getEventId()))
        .isEmpty()) {
      return;
    }
    singleEventRepository
        .findByIdAndAccountId(
            eventMapping.getEventId(), eventMapping.getConnection().getAccountId())
        .ifPresent(
            event -> {
              eventShareCommandService.deleteAllForSourceEvent(event.getId());
              singleEventRepository.delete(event);
            });
  }

  private void recordSyncConflict(
      GoogleCalendarEventMapping mapping, GoogleCalendarPageOwnership ownership) {
    operationJobService.recordSyncConflict(
        ownership.jobId(), mapping.getConnection().getAccountId(), ownership.workerToken());
  }
}
