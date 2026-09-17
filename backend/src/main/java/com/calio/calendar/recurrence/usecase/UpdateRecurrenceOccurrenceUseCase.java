package com.calio.calendar.recurrence.usecase;

import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceOccurrenceRequest;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventChangePublisher;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateRecurrenceOccurrenceUseCase {

  private final RecurrenceEventRepository recurrenceEventRepository;
  private final RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;
  private final Rfc5545RecurrenceEngine recurrenceEngine;
  private final RecurrenceEventChangePublisher changePublisher;

  public UpdateRecurrenceOccurrenceUseCase(
      RecurrenceEventRepository recurrenceEventRepository,
      RecurrenceEventOverrideRepository recurrenceEventOverrideRepository,
      Rfc5545RecurrenceEngine recurrenceEngine,
      RecurrenceEventChangePublisher changePublisher) {
    this.recurrenceEventRepository = recurrenceEventRepository;
    this.recurrenceEventOverrideRepository = recurrenceEventOverrideRepository;
    this.recurrenceEngine = recurrenceEngine;
    this.changePublisher = changePublisher;
  }

  @Transactional
  public EventResponse update(
      Long accountId, Long recurrenceEventId, UpdateRecurrenceOccurrenceRequest request) {
    RecurrenceEvent recurrenceEvent = lockRecurrenceEvent(accountId, recurrenceEventId);
    Optional<RecurrenceEventOverride> existingOverride =
        findOverrideOrRejectIneligible(recurrenceEvent, request.originStartAt());
    CanonicalSchedule schedule =
        CanonicalSchedule.recurrenceOverride(
            request.startAt(), request.endAt(), request.allDay(), request.timeZone());
    RecurrenceEventOverride recurrenceEventOverride =
        existingOverride.orElseGet(
            () ->
                RecurrenceEventOverride.active(
                    recurrenceEvent,
                    request.originStartAt(),
                    request.title(),
                    request.description(),
                    schedule));
    if (existingOverride.isPresent()) {
      recurrenceEventOverride.activate(request.title(), request.description(), schedule);
    }
    recurrenceEventOverrideRepository.saveAndFlush(recurrenceEventOverride);
    changePublisher.recurrenceOccurrenceUpdated(accountId, recurrenceEventOverride);
    return EventResponse.recurrenceOverride(recurrenceEventOverride);
  }

  private RecurrenceEvent lockRecurrenceEvent(Long accountId, Long recurrenceEventId) {
    return recurrenceEventRepository
        .findByIdAndAccountIdForUpdate(recurrenceEventId, accountId)
        .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
  }

  private Optional<RecurrenceEventOverride> findOverrideOrRejectIneligible(
      RecurrenceEvent recurrenceEvent, Instant originStartAt) {
    Optional<RecurrenceEventOverride> existingOverride =
        recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(
            recurrenceEvent.getId(), originStartAt);
    if (existingOverride.isEmpty()
        && !recurrenceEngine.containsOrigin(
            RecurrenceSchedule.from(recurrenceEvent),
            recurrenceEvent.getRecurrenceRules(),
            originStartAt)) {
      throw new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
    }
    return existingOverride;
  }
}
