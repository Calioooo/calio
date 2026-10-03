package com.calio.calendar.recurrence.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteRecurrenceOccurrenceUseCase {

  private final RecurrenceEventRepository recurrenceEventRepository;
  private final RecurrenceEventOverrideRepository overrideRepository;
  private final Rfc5545RecurrenceEngine recurrenceEngine;
  private final GoogleOperationJobEnqueueService jobEnqueueService;
  private final Clock clock;

  public DeleteRecurrenceOccurrenceUseCase(
      RecurrenceEventRepository recurrenceEventRepository,
      RecurrenceEventOverrideRepository overrideRepository,
      Rfc5545RecurrenceEngine recurrenceEngine,
      GoogleOperationJobEnqueueService jobEnqueueService,
      Clock clock) {
    this.recurrenceEventRepository = recurrenceEventRepository;
    this.overrideRepository = overrideRepository;
    this.recurrenceEngine = recurrenceEngine;
    this.jobEnqueueService = jobEnqueueService;
    this.clock = clock;
  }

  @Transactional
  public void delete(Long accountId, Long recurrenceId, Instant originStartAt) {
    var outboundOperation = jobEnqueueService.prepareOutboundOperation(accountId);
    RecurrenceEvent recurrenceEvent =
        recurrenceEventRepository
            .findByIdAndAccountIdForUpdate(recurrenceId, accountId)
            .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
    Optional<RecurrenceEventOverride> existing =
        overrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(recurrenceId, originStartAt);
    boolean generatedOrigin =
        existing.isEmpty()
            && recurrenceEngine.containsOrigin(
                RecurrenceSchedule.from(recurrenceEvent),
                recurrenceEvent.getRecurrenceRules(),
                originStartAt);
    RecurrenceEventOverride override =
        recurrenceEvent.excludeOccurrence(
            existing.orElse(null), originStartAt, generatedOrigin, clock.instant());
    overrideRepository.saveAndFlush(override);
    jobEnqueueService.enqueueRecurrenceOverrideDeleted(
        outboundOperation, recurrenceId, originStartAt);
  }
}
