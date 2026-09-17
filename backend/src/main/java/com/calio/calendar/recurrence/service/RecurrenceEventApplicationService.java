package com.calio.calendar.recurrence.service;

import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarRecurrenceJobKind;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceJobPayload;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceOverrideJobPayload;
import com.calio.calendar.recurrence.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceOccurrenceRequest;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RecurrenceEventApplicationService {

  private final RecurrenceEventService recurrenceEventService;
  private final GoogleOperationJobEnqueueService jobEnqueueService;

  public RecurrenceEventApplicationService(
      RecurrenceEventService recurrenceEventService,
      GoogleOperationJobEnqueueService jobEnqueueService) {
    this.recurrenceEventService = recurrenceEventService;
    this.jobEnqueueService = jobEnqueueService;
  }

  @Transactional
  public RecurrenceEventResponse createRecurrenceEvent(
      Long accountId, CreateRecurrenceEventRequest request) {
    RecurrenceEventResponse response =
        recurrenceEventService.createRecurrenceEvent(accountId, request);
    jobEnqueueService.enqueueRecurrence(
        accountId,
        response.recurrenceId(),
        GoogleCalendarRecurrenceJobKind.RECURRENCE_CREATE,
        GoogleRecurrenceJobPayload.from(response));
    return response;
  }

  public RecurrenceEventResponse getRecurrenceEvent(Long accountId, Long recurrenceId) {
    return recurrenceEventService.getRecurrenceEvent(accountId, recurrenceId);
  }

  @Transactional
  public RecurrenceEventResponse updateRecurrenceEvent(
      Long accountId, Long recurrenceId, UpdateRecurrenceEventRequest request) {
    RecurrenceEventResponse response =
        recurrenceEventService.updateRecurrenceEvent(accountId, recurrenceId, request);
    jobEnqueueService.enqueueRecurrence(
        accountId,
        recurrenceId,
        GoogleCalendarRecurrenceJobKind.RECURRENCE_UPDATE,
        GoogleRecurrenceJobPayload.from(response));
    return response;
  }

  @Transactional
  public EventResponse updateRecurrenceOccurrence(
      Long accountId, Long recurrenceId, UpdateRecurrenceOccurrenceRequest request) {
    EventResponse response =
        recurrenceEventService.updateRecurrenceOccurrence(accountId, recurrenceId, request);
    jobEnqueueService.enqueueRecurrenceOverride(
        accountId,
        recurrenceId,
        request.originStartAt(),
        GoogleRecurrenceOverrideJobPayload.from(response));
    return response;
  }

  @Transactional
  public void deleteRecurrenceEvent(Long accountId, Long recurrenceId) {
    recurrenceEventService.deleteRecurrenceEvent(accountId, recurrenceId);
    jobEnqueueService.enqueueRecurrenceDeleted(accountId, recurrenceId);
  }

  @Transactional
  public void deleteRecurrenceOccurrence(Long accountId, Long recurrenceId, Instant originStartAt) {
    recurrenceEventService.deleteRecurrenceOccurrence(accountId, recurrenceId, originStartAt);
    jobEnqueueService.enqueueRecurrenceOverrideDeleted(accountId, recurrenceId, originStartAt);
  }
}
