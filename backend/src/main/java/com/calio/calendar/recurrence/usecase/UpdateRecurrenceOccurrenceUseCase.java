package com.calio.calendar.recurrence.usecase;

import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceOverrideJobPayload;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceOccurrenceRequest;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
import com.calio.calendar.singleevent.controller.dto.EventResponse;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateRecurrenceOccurrenceUseCase {

  private final RecurrenceEventRepository recurrenceEventRepository;
  private final RecurrenceEventOverrideRepository overrideRepository;
  private final TagRepository tagRepository;
  private final Rfc5545RecurrenceEngine recurrenceEngine;
  private final GoogleOperationJobEnqueueService jobEnqueueService;

  public UpdateRecurrenceOccurrenceUseCase(
      RecurrenceEventRepository recurrenceEventRepository,
      RecurrenceEventOverrideRepository overrideRepository,
      TagRepository tagRepository,
      Rfc5545RecurrenceEngine recurrenceEngine,
      GoogleOperationJobEnqueueService jobEnqueueService) {
    this.recurrenceEventRepository = recurrenceEventRepository;
    this.overrideRepository = overrideRepository;
    this.tagRepository = tagRepository;
    this.recurrenceEngine = recurrenceEngine;
    this.jobEnqueueService = jobEnqueueService;
  }

  @Transactional
  public EventResponse update(
      Long accountId, Long recurrenceId, UpdateRecurrenceOccurrenceRequest request) {
    CanonicalSchedule schedule =
        CanonicalSchedule.recurrenceOverride(
            request.startAt(), request.endAt(), request.allDay(), request.timeZone());
    var outboundOperation = jobEnqueueService.prepareOutboundOperation(accountId);
    RecurrenceEvent recurrenceEvent =
        recurrenceEventRepository
            .findByIdAndAccountIdForUpdate(recurrenceId, accountId)
            .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
    Optional<RecurrenceEventOverride> existing =
        overrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(
            recurrenceId, request.originStartAt());
    boolean generatedOrigin =
        existing.isEmpty()
            && recurrenceEngine.containsOrigin(
                RecurrenceSchedule.from(recurrenceEvent),
                recurrenceEvent.getRecurrenceRules(),
                request.originStartAt());
    RecurrenceEventOverride override =
        recurrenceEvent.changeOccurrence(
            existing.orElse(null),
            request.originStartAt(),
            generatedOrigin,
            request.title(),
            request.description(),
            schedule);
    overrideRepository.saveAndFlush(override);
    jobEnqueueService.enqueueRecurrenceOverride(
        outboundOperation,
        recurrenceId,
        request.originStartAt(),
        GoogleRecurrenceOverrideJobPayload.from(override));
    Tag tag =
        tagRepository
            .findById(recurrenceEvent.getTagId())
            .orElseThrow(() -> new CalioException(ErrorCode.TAG_NOT_FOUND));
    return EventResponse.recurrenceOverride(override, tag);
  }
}
