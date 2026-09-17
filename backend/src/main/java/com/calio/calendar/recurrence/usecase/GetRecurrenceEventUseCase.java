package com.calio.calendar.recurrence.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetRecurrenceEventUseCase {

    private final RecurrenceEventRepository recurrenceEventRepository;

    public GetRecurrenceEventUseCase(RecurrenceEventRepository recurrenceEventRepository) {
        this.recurrenceEventRepository = recurrenceEventRepository;
    }

    @Transactional(readOnly = true)
    public RecurrenceEventResponse get(Long accountId, Long recurrenceEventId) {
        return recurrenceEventRepository.findByIdAndAccount_Id(recurrenceEventId, accountId)
                .map(recurrenceEvent -> RecurrenceEventResponse.from(recurrenceEvent, true))
                .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
    }
}
