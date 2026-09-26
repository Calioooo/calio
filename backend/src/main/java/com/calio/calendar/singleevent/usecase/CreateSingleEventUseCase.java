package com.calio.calendar.singleevent.usecase;

import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService.OutboundOperation;
import com.calio.calendar.singleevent.controller.dto.CreateSingleEventRequest;
import com.calio.calendar.singleevent.controller.dto.EventResponse;
import com.calio.calendar.singleevent.domain.SingleEvent;
import com.calio.calendar.singleevent.domain.SingleEventSchedule;
import com.calio.calendar.singleevent.domain.SingleEventTitle;
import com.calio.calendar.singleevent.repository.SingleEventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateSingleEventUseCase {

  private final AccountRepository accountRepository;
  private final SingleEventRepository eventRepository;
  private final TagRepository tagRepository;
  private final GoogleOperationJobEnqueueService jobEnqueueService;

  public CreateSingleEventUseCase(
      AccountRepository accountRepository,
      SingleEventRepository eventRepository,
      TagRepository tagRepository,
      GoogleOperationJobEnqueueService jobEnqueueService) {
    this.accountRepository = accountRepository;
    this.eventRepository = eventRepository;
    this.tagRepository = tagRepository;
    this.jobEnqueueService = jobEnqueueService;
  }

  @Transactional
  public EventResponse create(Long accountId, CreateSingleEventRequest request) {
    OutboundOperation outboundOperation = jobEnqueueService.prepareOutboundOperation(accountId);
    accountRepository
        .findById(accountId)
        .orElseThrow(() -> new CalioException(ErrorCode.ACCOUNT_NOT_FOUND));
    Tag tag = getPersonalTagOrDefault(accountId, request.tagId());
    SingleEvent event =
        eventRepository.save(
            new SingleEvent(
                new SingleEventTitle(request.title()),
                request.description(),
                new SingleEventSchedule(
                    request.startAt(), request.endAt(), request.allDay(), request.timeZone()),
                tag.getId(),
                accountId));
    jobEnqueueService.enqueueEventCreated(outboundOperation, event);
    return EventResponse.from(event, tag);
  }

  private Tag getPersonalTagOrDefault(Long accountId, Long tagId) {
    if (tagId == null) {
      return tagRepository
          .findPersonalFallbackTag()
          .orElseThrow(() -> new CalioException(ErrorCode.DEFAULT_TAG_NOT_FOUND));
    }
    return tagRepository
        .findPersonalDefaultTagById(tagId)
        .or(() -> tagRepository.findPersonalCustomTagById(accountId, tagId))
        .orElseThrow(() -> new CalioException(ErrorCode.TAG_NOT_FOUND));
  }
}
