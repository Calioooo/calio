package com.calio.calendar.recurrence.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class RecurrenceEventQueryService {

    private final RecurrenceEventRepository recurrenceEventRepository;
    private final RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;

    public RecurrenceEventQueryService(
            RecurrenceEventRepository recurrenceEventRepository,
            RecurrenceEventOverrideRepository recurrenceEventOverrideRepository
    ) {
        this.recurrenceEventRepository = recurrenceEventRepository;
        this.recurrenceEventOverrideRepository = recurrenceEventOverrideRepository;
    }

    public RecurrenceEventResponse getRecurrenceEvent(Long accountId, Long recurrenceId) {
        return recurrenceEventRepository.findByIdAndAccount_Id(recurrenceId, accountId)
                .map(RecurrenceEventResponse::from)
                .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
    }

    public RecurrenceEvent findRecurrenceEventForUpdate(Long accountId, Long recurrenceId) {
        return recurrenceEventRepository.findByIdAndAccountIdForUpdate(recurrenceId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
    }

    public Optional<RecurrenceEventOverride> findOverride(Long recurrenceId, Instant originStartAt) {
        return recurrenceEventOverrideRepository
                .findByRecurrenceEvent_IdAndOriginStartAt(recurrenceId, originStartAt);
    }
}
