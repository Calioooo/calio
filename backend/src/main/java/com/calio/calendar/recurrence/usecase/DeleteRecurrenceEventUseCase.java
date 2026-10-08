package com.calio.calendar.recurrence.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService.OutboundOperation;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteRecurrenceEventUseCase {

  private final RecurrenceEventRepository recurrenceEventRepository;
  private final PersonalRecurrenceGroupShareRepository shareRepository;
  private final GoogleOperationJobEnqueueService jobEnqueueService;

  public DeleteRecurrenceEventUseCase(
      RecurrenceEventRepository recurrenceEventRepository,
      PersonalRecurrenceGroupShareRepository shareRepository,
      GoogleOperationJobEnqueueService jobEnqueueService) {
    this.recurrenceEventRepository = recurrenceEventRepository;
    this.shareRepository = shareRepository;
    this.jobEnqueueService = jobEnqueueService;
  }

  @Transactional
  public void delete(Long accountId, Long recurrenceId) {
    OutboundOperation outboundOperation = jobEnqueueService.prepareOutboundOperation(accountId);
    RecurrenceEvent recurrenceEvent =
        recurrenceEventRepository
            .findByIdAndAccountIdForUpdate(recurrenceId, accountId)
            .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
    shareRepository.deleteAllByRecurrenceEventId(recurrenceId);
    recurrenceEventRepository.delete(recurrenceEvent);
    jobEnqueueService.enqueueRecurrenceDeleted(outboundOperation, recurrenceId);
  }
}
