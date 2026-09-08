package com.calio.calendar.recurrence.service;

import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarRecurrenceJobKind;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceMasterJobPayload;
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

    private final RecurrenceEventService recurrenceService;
    private final GoogleOperationJobEnqueueService jobEnqueueService;

    public RecurrenceEventApplicationService(
            RecurrenceEventService recurrenceService,
            GoogleOperationJobEnqueueService jobEnqueueService
    ) {
        this.recurrenceService = recurrenceService;
        this.jobEnqueueService = jobEnqueueService;
    }

    @Transactional
    public RecurrenceEventResponse createRecurrenceEvent(Long accountId, CreateRecurrenceEventRequest request) {
        RecurrenceEventResponse response = recurrenceService.createRecurrenceEvent(accountId, request);
        jobEnqueueService.enqueueRecurrenceMaster(accountId, response.recurrenceId(),
                GoogleCalendarRecurrenceJobKind.MASTER_CREATE,
                GoogleRecurrenceMasterJobPayload.from(response));
        return response;
    }

    public RecurrenceEventResponse getRecurrenceEvent(Long accountId, Long recurrenceId) {
        return recurrenceService.getRecurrenceEvent(accountId, recurrenceId);
    }

    @Transactional
    public RecurrenceEventResponse updateRecurrenceEvent(
            Long accountId, Long recurrenceId, UpdateRecurrenceEventRequest request
    ) {
        RecurrenceEventResponse response = recurrenceService.updateRecurrenceEvent(accountId, recurrenceId, request);
        jobEnqueueService.enqueueRecurrenceMaster(accountId, recurrenceId,
                GoogleCalendarRecurrenceJobKind.MASTER_UPDATE,
                GoogleRecurrenceMasterJobPayload.from(response));
        return response;
    }

    @Transactional
    public EventResponse updateRecurrenceOccurrence(
            Long accountId, Long recurrenceId, UpdateRecurrenceOccurrenceRequest request
    ) {
        EventResponse response = recurrenceService.updateRecurrenceOccurrence(accountId, recurrenceId, request);
        jobEnqueueService.enqueueRecurrenceOverride(accountId, recurrenceId, request.originStartAt(),
                GoogleRecurrenceOverrideJobPayload.from(response));
        return response;
    }

    @Transactional
    public void deleteRecurrenceEvent(Long accountId, Long recurrenceId) {
        recurrenceService.deleteRecurrenceEvent(accountId, recurrenceId);
        jobEnqueueService.enqueueRecurrenceMasterDeleted(accountId, recurrenceId);
    }

    @Transactional
    public void deleteRecurrenceOccurrence(Long accountId, Long recurrenceId, Instant originStartAt) {
        recurrenceService.deleteRecurrenceOccurrence(accountId, recurrenceId, originStartAt);
        jobEnqueueService.enqueueRecurrenceOverrideDeleted(accountId, recurrenceId, originStartAt);
    }
}
