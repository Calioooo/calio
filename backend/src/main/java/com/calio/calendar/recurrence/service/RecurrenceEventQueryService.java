package com.calio.calendar.recurrence.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
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

    public RecurrenceEvent getRecurrenceEvent(Long accountId, Long recurrenceId) {
        return recurrenceEventRepository.findByIdAndAccount_Id(recurrenceId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
    }

    public Optional<RecurrenceEventOverride> getOverrideIfExists(Long recurrenceId, Instant originStartAt) {
        return recurrenceEventOverrideRepository
                .findByRecurrenceEvent_IdAndOriginStartAt(recurrenceId, originStartAt);
    }

    public List<RecurrenceEvent> listExpansionCandidatesStartedBefore(Long accountId, Instant to) {
        return recurrenceEventRepository.findExpansionCandidatesStartedBefore(accountId, to);
    }

    public List<RecurrenceEventOverride> listOverrides(Long recurrenceId, Collection<Instant> originStartAts) {
        return recurrenceEventOverrideRepository
                .findByRecurrenceEvent_IdAndOriginStartAtIn(recurrenceId, originStartAts);
    }

    public List<RecurrenceEventOverride> listActiveOverlappingOverrides(
            Long accountId,
            Instant from,
            Instant to
    ) {
        return recurrenceEventOverrideRepository.findActiveOverlappingOverrides(accountId, from, to);
    }

    public List<RecurrenceEventOverride> listActiveOverlappingOverridesForRecurrence(
            Long recurrenceId,
            Instant from,
            Instant to
    ) {
        return recurrenceEventOverrideRepository.findActiveOverlappingOverridesForRecurrence(
                recurrenceId,
                from,
                to
        );
    }
}
