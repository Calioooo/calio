package com.calio.calendar.singleevent.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService.OutboundOperation;
import com.calio.calendar.singleevent.controller.dto.EventResponse;
import com.calio.calendar.singleevent.controller.dto.UpdateSingleEventRequest;
import com.calio.calendar.singleevent.domain.SingleEvent;
import com.calio.calendar.singleevent.domain.SingleEventSchedule;
import com.calio.calendar.singleevent.domain.SingleEventTitle;
import com.calio.calendar.singleevent.repository.SingleEventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.service.TagQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateSingleEventUseCase {

  private final SingleEventRepository eventRepository;
  private final TagQueryService tagQueryService;
  private final GoogleOperationJobEnqueueService jobEnqueueService;

  public UpdateSingleEventUseCase(
      SingleEventRepository eventRepository,
      TagQueryService tagQueryService,
      GoogleOperationJobEnqueueService jobEnqueueService) {
    this.eventRepository = eventRepository;
    this.tagQueryService = tagQueryService;
    this.jobEnqueueService = jobEnqueueService;
  }

  @Transactional
  public EventResponse update(Long accountId, Long eventId, UpdateSingleEventRequest request) {
    OutboundOperation outboundOperation = jobEnqueueService.prepareOutboundOperation(accountId);
    SingleEvent event =
        eventRepository
            .findByIdAndAccountIdForUpdate(eventId, accountId)
            .orElseThrow(() -> new CalioException(ErrorCode.EVENT_NOT_FOUND));
    Tag tag = tagQueryService.getTagOrDefault(accountId, request.tagId());
    event.replace(
        new SingleEventTitle(request.title()),
        request.description(),
        new SingleEventSchedule(
            request.startAt(), request.endAt(), request.allDay(), request.timeZone()));
    event.changeTag(tag.getId());
    eventRepository.flush();
    jobEnqueueService.enqueueEventUpdated(outboundOperation, event);
    return EventResponse.from(event, tag);
  }
}
