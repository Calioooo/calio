package com.calio.calendar.recurrence.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService.OutboundOperation;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarRecurrenceJobKind;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceJobPayload;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceOverrideJobPayload;
import com.calio.calendar.recurrence.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceOccurrenceRequest;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.sharing.recurrence.service.PersonalRecurrenceGroupShareCommandService;
import com.calio.calendar.singleevent.controller.dto.EventResponse;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RecurrenceEventService {

  private final RecurrenceEventQueryService recurrenceEventQueryService;
  private final RecurrenceEventCommandService recurrenceEventCommandService;
  private final AccountRepository accountRepository;
  private final TagRepository tagRepository;
  private final Rfc5545RecurrenceEngine recurrenceEngine;
  private final Clock clock;
  private final PersonalRecurrenceGroupShareCommandService recurrenceShareCommandService;
  private final GoogleOperationJobEnqueueService jobEnqueueService;

  public RecurrenceEventService(
      RecurrenceEventQueryService recurrenceEventQueryService,
      RecurrenceEventCommandService recurrenceEventCommandService,
      AccountRepository accountRepository,
      TagRepository tagRepository,
      Rfc5545RecurrenceEngine recurrenceEngine,
      Clock clock,
      PersonalRecurrenceGroupShareCommandService recurrenceShareCommandService,
      GoogleOperationJobEnqueueService jobEnqueueService) {
    this.recurrenceEventQueryService = recurrenceEventQueryService;
    this.recurrenceEventCommandService = recurrenceEventCommandService;
    this.accountRepository = accountRepository;
    this.tagRepository = tagRepository;
    this.recurrenceEngine = recurrenceEngine;
    this.clock = clock;
    this.recurrenceShareCommandService = recurrenceShareCommandService;
    this.jobEnqueueService = jobEnqueueService;
  }

  @Transactional
  public RecurrenceEventResponse createRecurrenceEvent(
      Long accountId, CreateRecurrenceEventRequest request) {
    RecurrenceSchedule schedule = createSchedule(request);
    List<String> recurrenceRules = recurrenceEngine.validate(schedule, request.recurrence());
    OutboundOperation outboundOperation = jobEnqueueService.prepareOutboundOperation(accountId);
    Account account =
        accountRepository
            .findById(accountId)
            .orElseThrow(() -> new CalioException(ErrorCode.ACCOUNT_NOT_FOUND));
    Tag tag = getPersonalTagOrDefault(accountId, request.tagId());
    RecurrenceEvent recurrenceEvent =
        recurrenceEventCommandService.createRecurrenceEvent(
            new RecurrenceEvent(
                request.title(), request.description(), schedule, recurrenceRules, tag, account));
    jobEnqueueService.enqueueRecurrence(
        outboundOperation,
        recurrenceEvent.getId(),
        GoogleCalendarRecurrenceJobKind.RECURRENCE_CREATE,
        GoogleRecurrenceJobPayload.from(recurrenceEvent));
    return RecurrenceEventResponse.from(recurrenceEvent);
  }

  public RecurrenceEventResponse getRecurrenceEvent(Long accountId, Long recurrenceId) {
    return RecurrenceEventResponse.from(
        recurrenceEventQueryService.getRecurrenceEvent(accountId, recurrenceId));
  }

  public EventResponse getRecurrenceOccurrence(
      Long accountId, Long recurrenceId, Instant originStartAt) {
    RecurrenceEvent recurrenceEvent =
        recurrenceEventQueryService.getRecurrenceEvent(accountId, recurrenceId);
    Optional<RecurrenceEventOverride> override =
        recurrenceEventQueryService.getOverrideIfExists(recurrenceId, originStartAt);
    if (override.isPresent()) {
      if (override.get().isDeleted()) {
        throw new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
      }
      return EventResponse.recurrenceOverride(override.get());
    }
    return EventResponse.recurrenceOccurrence(
        recurrenceEvent, findGeneratedOccurrence(recurrenceEvent, originStartAt));
  }

  @Transactional
  public RecurrenceEventResponse updateRecurrenceEvent(
      Long accountId, Long recurrenceId, UpdateRecurrenceEventRequest request) {
    RecurrenceSchedule schedule = createSchedule(request);
    List<String> recurrenceRules = recurrenceEngine.validate(schedule, request.recurrence());
    OutboundOperation outboundOperation = jobEnqueueService.prepareOutboundOperation(accountId);
    RecurrenceEvent recurrenceEvent =
        recurrenceEventCommandService.lockRecurrenceEvent(accountId, recurrenceId);
    Tag tag = getPersonalTagOrDefault(accountId, request.tagId());
    recurrenceEventCommandService.updateRecurrenceEvent(
        recurrenceEvent, request, schedule, recurrenceRules, tag);
    jobEnqueueService.enqueueRecurrence(
        outboundOperation,
        recurrenceId,
        GoogleCalendarRecurrenceJobKind.RECURRENCE_UPDATE,
        GoogleRecurrenceJobPayload.from(recurrenceEvent));
    return RecurrenceEventResponse.from(recurrenceEvent);
  }

  @Transactional
  public EventResponse updateRecurrenceOccurrence(
      Long accountId, Long recurrenceId, UpdateRecurrenceOccurrenceRequest request) {
    CanonicalSchedule schedule =
        CanonicalSchedule.recurrenceOverride(
            request.startAt(), request.endAt(), request.allDay(), request.timeZone());
    OutboundOperation outboundOperation = jobEnqueueService.prepareOutboundOperation(accountId);
    RecurrenceEvent recurrenceEvent =
        recurrenceEventCommandService.lockRecurrenceEvent(accountId, recurrenceId);
    Optional<RecurrenceEventOverride> existingOverride =
        findOverrideOrRejectIneligible(recurrenceEvent, request.originStartAt());
    RecurrenceEventOverride override =
        existingOverride.orElseGet(
            () ->
                RecurrenceEventOverride.active(
                    recurrenceEvent,
                    request.originStartAt(),
                    request.title(),
                    request.description(),
                    schedule));
    if (existingOverride.isPresent()) {
      override.activate(request.title(), request.description(), schedule);
    }
    recurrenceEventCommandService.createOrUpdateRecurrenceOverride(override);
    jobEnqueueService.enqueueRecurrenceOverride(
        outboundOperation,
        recurrenceId,
        request.originStartAt(),
        GoogleRecurrenceOverrideJobPayload.from(override));
    return EventResponse.recurrenceOverride(override);
  }

  @Transactional
  public void deleteRecurrenceEvent(Long accountId, Long recurrenceId) {
    OutboundOperation outboundOperation = jobEnqueueService.prepareOutboundOperation(accountId);
    recurrenceEventCommandService.lockRecurrenceEvent(accountId, recurrenceId);
    recurrenceShareCommandService.deleteAllForSourceRecurrence(recurrenceId);
    recurrenceEventCommandService.deleteRecurrenceOverridesByRecurrenceEventIds(
        List.of(recurrenceId));
    recurrenceEventCommandService.deleteRecurrenceEventsByIds(List.of(recurrenceId));
    jobEnqueueService.enqueueRecurrenceDeleted(outboundOperation, recurrenceId);
  }

  @Transactional
  public void deleteRecurrenceOccurrence(Long accountId, Long recurrenceId, Instant originStartAt) {
    OutboundOperation outboundOperation = jobEnqueueService.prepareOutboundOperation(accountId);
    RecurrenceEvent recurrenceEvent =
        recurrenceEventCommandService.lockRecurrenceEvent(accountId, recurrenceId);
    Optional<RecurrenceEventOverride> existingOverride =
        findOverrideOrRejectIneligible(recurrenceEvent, originStartAt);
    Instant deletedAt = Instant.now(clock);
    recurrenceEventCommandService.deleteRecurrenceOccurrence(
        recurrenceEvent, existingOverride, originStartAt, deletedAt);
    jobEnqueueService.enqueueRecurrenceOverrideDeleted(
        outboundOperation, recurrenceId, originStartAt);
  }

  private RecurrenceSchedule createSchedule(CreateRecurrenceEventRequest request) {
    return RecurrenceSchedule.create(
        request.allDay(),
        request.firstOccurrenceStartAt(),
        request.firstOccurrenceEndAt(),
        request.timeZone());
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

  private RecurrenceSchedule createSchedule(UpdateRecurrenceEventRequest request) {
    return RecurrenceSchedule.create(
        request.allDay(),
        request.firstOccurrenceStartAt(),
        request.firstOccurrenceEndAt(),
        request.timeZone());
  }

  private Optional<RecurrenceEventOverride> findOverrideOrRejectIneligible(
      RecurrenceEvent recurrenceEvent, Instant originStartAt) {
    Optional<RecurrenceEventOverride> existingOverride =
        recurrenceEventQueryService.getOverrideIfExists(recurrenceEvent.getId(), originStartAt);
    if (existingOverride.isEmpty() && !recurrenceContainsOrigin(recurrenceEvent, originStartAt)) {
      throw new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
    }
    return existingOverride;
  }

  private boolean recurrenceContainsOrigin(RecurrenceEvent recurrenceEvent, Instant originStartAt) {
    return recurrenceEngine.containsOrigin(
        RecurrenceSchedule.from(recurrenceEvent),
        recurrenceEvent.getRecurrenceRules(),
        originStartAt);
  }

  private RecurrenceOccurrence findGeneratedOccurrence(
      RecurrenceEvent recurrenceEvent, Instant originStartAt) {
    return recurrenceEngine
        .expand(
            RecurrenceSchedule.from(recurrenceEvent),
            recurrenceEvent.getRecurrenceRules(),
            originStartAt,
            originStartAt.plusNanos(1))
        .stream()
        .filter(occurrence -> originStartAt.equals(occurrence.originStartAt()))
        .findFirst()
        .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND));
  }
}
