package com.calio.calendar.recurrence.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteRecurrenceEventUseCase {

  private final RecurrenceEventRepository recurrenceEventRepository;
  private final RecurrenceEventOverrideRepository overrideRepository;
  private final PersonalRecurrenceGroupShareRepository shareRepository;
  private final GoogleOperationJobEnqueueService jobEnqueueService;

  public DeleteRecurrenceEventUseCase(
      RecurrenceEventRepository recurrenceEventRepository,
      RecurrenceEventOverrideRepository overrideRepository,
      PersonalRecurrenceGroupShareRepository shareRepository,
      GoogleOperationJobEnqueueService jobEnqueueService) {
    this.recurrenceEventRepository = recurrenceEventRepository;
    this.overrideRepository = overrideRepository;
    this.shareRepository = shareRepository;
    this.jobEnqueueService = jobEnqueueService;
  }

  @Transactional
  public void delete(Long accountId, Long recurrenceId) {
    var outboundOperation = jobEnqueueService.prepareOutboundOperation(accountId);
    recurrenceEventRepository
        .findByIdAndAccountIdForUpdate(recurrenceId, accountId)
        .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
    shareRepository.deleteAllByRecurrenceEventId(recurrenceId);
    overrideRepository.deleteAllByRecurrenceEventIds(List.of(recurrenceId));
    recurrenceEventRepository.deleteAllByIds(List.of(recurrenceId));
    jobEnqueueService.enqueueRecurrenceDeleted(outboundOperation, recurrenceId);
  }
}
