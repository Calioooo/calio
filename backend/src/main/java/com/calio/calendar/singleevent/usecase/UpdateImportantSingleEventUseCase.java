package com.calio.calendar.singleevent.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingQueryService;
import com.calio.calendar.singleevent.controller.dto.EventResponse;
import com.calio.calendar.singleevent.domain.SingleEvent;
import com.calio.calendar.singleevent.repository.SingleEventRepository;
import com.calio.calendar.tag.service.TagQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateImportantSingleEventUseCase {

  private final SingleEventRepository eventRepository;
  private final GoogleCalendarEventMappingQueryService eventMappingQueryService;
  private final TagQueryService tagQueryService;

  public UpdateImportantSingleEventUseCase(
      SingleEventRepository eventRepository,
      GoogleCalendarEventMappingQueryService eventMappingQueryService,
      TagQueryService tagQueryService) {
    this.eventRepository = eventRepository;
    this.eventMappingQueryService = eventMappingQueryService;
    this.tagQueryService = tagQueryService;
  }

  @Transactional
  public EventResponse update(Long accountId, Long eventId, boolean importantEvent) {
    SingleEvent event =
        eventRepository
            .findByIdAndAccountIdForUpdate(eventId, accountId)
            .orElseThrow(() -> new CalioException(ErrorCode.EVENT_NOT_FOUND));
    if (eventMappingQueryService.hasExternalEventMapping(eventId, accountId)) {
      throw new CalioException(ErrorCode.EXTERNAL_EVENT_MUTATION_NOT_SUPPORTED);
    }
    event.changeImportantEvent(importantEvent);
    eventRepository.flush();
    return EventResponse.from(event, tagQueryService.getTag(accountId, event.getTagId()));
  }
}
