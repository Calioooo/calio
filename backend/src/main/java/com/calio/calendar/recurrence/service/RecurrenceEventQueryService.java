package com.calio.calendar.recurrence.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RecurrenceEventQueryService {

    private final RecurrenceEventRepository recurrenceEventRepository;

    public RecurrenceEventQueryService(RecurrenceEventRepository recurrenceEventRepository) {
        this.recurrenceEventRepository = recurrenceEventRepository;
    }

    public RecurrenceEventResponse getRecurrenceEvent(Long accountId, Long recurrenceId) {
        return recurrenceEventRepository.findByIdAndAccount_Id(recurrenceId, accountId)
                .map(RecurrenceEventResponse::from)
                .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
    }
}
