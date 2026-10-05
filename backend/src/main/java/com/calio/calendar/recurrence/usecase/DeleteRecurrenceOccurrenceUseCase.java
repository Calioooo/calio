package com.calio.calendar.recurrence.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService.OutboundOperation;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteRecurrenceOccurrenceUseCase {

  private final RecurrenceEventRepository recurrenceEventRepository;
  private final Rfc5545RecurrenceEngine recurrenceEngine;
  private final GoogleOperationJobEnqueueService jobEnqueueService;
  private final Clock clock;

  public DeleteRecurrenceOccurrenceUseCase(
      RecurrenceEventRepository recurrenceEventRepository,
      Rfc5545RecurrenceEngine recurrenceEngine,
      GoogleOperationJobEnqueueService jobEnqueueService,
      Clock clock) {
    this.recurrenceEventRepository = recurrenceEventRepository;
    this.recurrenceEngine = recurrenceEngine;
    this.jobEnqueueService = jobEnqueueService;
    this.clock = clock;
  }

  @Transactional
  public void delete(Long accountId, Long recurrenceId, Instant originStartAt) {
    OutboundOperation outboundOperation = jobEnqueueService.prepareOutboundOperation(accountId);
    RecurrenceEvent recurrenceEvent =
        recurrenceEventRepository
            .findByIdAndAccountIdForUpdate(recurrenceId, accountId)
            .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
    recurrenceEvent.requireOccurrence(originStartAt, recurrenceEngine);
    recurrenceEvent.excludeOccurrence(originStartAt, clock.instant());
    recurrenceEventRepository.flush();
    jobEnqueueService.enqueueRecurrenceOverrideDeleted(
        outboundOperation, recurrenceId, originStartAt);
  }
}
