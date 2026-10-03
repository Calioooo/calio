package com.calio.calendar.recurrence.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarRecurrenceJobKind;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceJobPayload;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateRecurrenceEventUseCase {

  private final RecurrenceEventRepository recurrenceEventRepository;
  private final TagRepository tagRepository;
  private final Rfc5545RecurrenceEngine recurrenceEngine;
  private final GoogleOperationJobEnqueueService jobEnqueueService;

  public UpdateRecurrenceEventUseCase(
      RecurrenceEventRepository recurrenceEventRepository,
      TagRepository tagRepository,
      Rfc5545RecurrenceEngine recurrenceEngine,
      GoogleOperationJobEnqueueService jobEnqueueService) {
    this.recurrenceEventRepository = recurrenceEventRepository;
    this.tagRepository = tagRepository;
    this.recurrenceEngine = recurrenceEngine;
    this.jobEnqueueService = jobEnqueueService;
  }

  @Transactional
  public RecurrenceEventResponse update(
      Long accountId, Long recurrenceId, UpdateRecurrenceEventRequest request) {
    RecurrenceSchedule schedule =
        RecurrenceSchedule.create(
            request.allDay(),
            request.firstOccurrenceStartAt(),
            request.firstOccurrenceEndAt(),
            request.timeZone());
    List<String> recurrenceRules = recurrenceEngine.validate(schedule, request.recurrence());
    var outboundOperation = jobEnqueueService.prepareOutboundOperation(accountId);
    RecurrenceEvent recurrenceEvent =
        recurrenceEventRepository
            .findByIdAndAccountIdForUpdate(recurrenceId, accountId)
            .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
    Tag tag = findTagOrDefault(accountId, request.tagId());
    recurrenceEvent.update(
        request.title(), request.description(), schedule, recurrenceRules, tag.getId());
    recurrenceEventRepository.flush();
    jobEnqueueService.enqueueRecurrence(
        outboundOperation,
        recurrenceId,
        GoogleCalendarRecurrenceJobKind.RECURRENCE_UPDATE,
        GoogleRecurrenceJobPayload.from(recurrenceEvent));
    return RecurrenceEventResponse.from(recurrenceEvent, tag);
  }

  private Tag findTagOrDefault(Long accountId, Long tagId) {
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
