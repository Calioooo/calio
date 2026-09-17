package com.calio.calendar.recurrence.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventChangePublisher;
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
    private final RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;
    private final Rfc5545RecurrenceEngine recurrenceEngine;
    private final RecurrenceEventChangePublisher changePublisher;
    private final Clock clock;

    public DeleteRecurrenceOccurrenceUseCase(
            RecurrenceEventRepository recurrenceEventRepository,
            RecurrenceEventOverrideRepository recurrenceEventOverrideRepository,
            Rfc5545RecurrenceEngine recurrenceEngine,
            RecurrenceEventChangePublisher changePublisher,
            Clock clock
    ) {
        this.recurrenceEventRepository = recurrenceEventRepository;
        this.recurrenceEventOverrideRepository = recurrenceEventOverrideRepository;
        this.recurrenceEngine = recurrenceEngine;
        this.changePublisher = changePublisher;
        this.clock = clock;
    }

    @Transactional
    public void delete(Long accountId, Long recurrenceEventId, Instant originStartAt) {
        RecurrenceEvent recurrenceEvent = recurrenceEventRepository
                .findByIdAndAccountIdForUpdate(recurrenceEventId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
        Optional<RecurrenceEventOverride> existingOverride = recurrenceEventOverrideRepository
                .findByRecurrenceEvent_IdAndOriginStartAt(recurrenceEventId, originStartAt);
        if (existingOverride.isEmpty() && !recurrenceEngine.containsOrigin(
                RecurrenceSchedule.from(recurrenceEvent),
                recurrenceEvent.getRecurrenceRules(),
                originStartAt
        )) {
            throw new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
        }
        Instant deletedAt = Instant.now(clock);
        RecurrenceEventOverride recurrenceEventOverride = existingOverride.orElseGet(
                () -> RecurrenceEventOverride.deleted(
                        recurrenceEvent,
                        originStartAt,
                        deletedAt
                )
        );
        if (existingOverride.isPresent()) {
            recurrenceEventOverride.markDeleted(deletedAt);
        }
        recurrenceEventOverrideRepository.saveAndFlush(recurrenceEventOverride);
        changePublisher.recurrenceOccurrenceDeleted(accountId, recurrenceEventId, originStartAt);
    }
}
