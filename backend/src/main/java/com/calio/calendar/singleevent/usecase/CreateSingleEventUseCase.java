package com.calio.calendar.singleevent.usecase;

import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService;
import com.calio.calendar.singleevent.controller.dto.CreateSingleEventRequest;
import com.calio.calendar.singleevent.controller.dto.EventResponse;
import com.calio.calendar.singleevent.domain.SingleEvent;
import com.calio.calendar.singleevent.domain.SingleEventSchedule;
import com.calio.calendar.singleevent.domain.SingleEventTitle;
import com.calio.calendar.singleevent.repository.SingleEventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.service.TagQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateSingleEventUseCase {

  private final AccountRepository accountRepository;
  private final SingleEventRepository eventRepository;
  private final TagQueryService tagQueryService;
  private final GoogleOperationJobEnqueueService jobEnqueueService;

  public CreateSingleEventUseCase(
      AccountRepository accountRepository,
      SingleEventRepository eventRepository,
      TagQueryService tagQueryService,
      GoogleOperationJobEnqueueService jobEnqueueService) {
    this.accountRepository = accountRepository;
    this.eventRepository = eventRepository;
    this.tagQueryService = tagQueryService;
    this.jobEnqueueService = jobEnqueueService;
  }

  @Transactional
  public EventResponse create(Long accountId, CreateSingleEventRequest request) {
    accountRepository
        .findById(accountId)
        .orElseThrow(() -> new CalioException(ErrorCode.ACCOUNT_NOT_FOUND));
    Tag tag = tagQueryService.getTagOrDefault(accountId, request.tagId());
    SingleEvent event =
        eventRepository.save(
            new SingleEvent(
                new SingleEventTitle(request.title()),
                request.description(),
                new SingleEventSchedule(
                    request.startAt(), request.endAt(), request.allDay(), request.timeZone()),
                tag.getId(),
                accountId));
    jobEnqueueService.enqueueEventCreated(accountId, event);
    return EventResponse.from(event, tag);
  }
}
