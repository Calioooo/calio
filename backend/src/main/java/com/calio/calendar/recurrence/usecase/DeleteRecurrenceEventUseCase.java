package com.calio.calendar.recurrence.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.recurrence.domain.RecurrenceEventChangePublisher;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteRecurrenceEventUseCase {

    private final RecurrenceEventRepository recurrenceEventRepository;
    private final RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;
    private final EventRepository eventRepository;
    private final PersonalRecurrenceGroupShareRepository recurrenceGroupShareRepository;
    private final RecurrenceEventChangePublisher changePublisher;

    public DeleteRecurrenceEventUseCase(
            RecurrenceEventRepository recurrenceEventRepository,
            RecurrenceEventOverrideRepository recurrenceEventOverrideRepository,
            EventRepository eventRepository,
            PersonalRecurrenceGroupShareRepository recurrenceGroupShareRepository,
            RecurrenceEventChangePublisher changePublisher
    ) {
        this.recurrenceEventRepository = recurrenceEventRepository;
        this.recurrenceEventOverrideRepository = recurrenceEventOverrideRepository;
        this.eventRepository = eventRepository;
        this.recurrenceGroupShareRepository = recurrenceGroupShareRepository;
        this.changePublisher = changePublisher;
    }

    @Transactional
    public void delete(Long accountId, Long recurrenceEventId) {
        recurrenceEventRepository.findByIdAndAccountIdForUpdate(recurrenceEventId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
        recurrenceGroupShareRepository.deleteAllByRecurrenceEventId(recurrenceEventId);
        recurrenceEventOverrideRepository.deleteAllByRecurrenceEventIds(List.of(recurrenceEventId));
        eventRepository.deleteAllByRecurrenceEventIds(List.of(recurrenceEventId));
        recurrenceEventRepository.deleteAllByIds(List.of(recurrenceEventId));
        changePublisher.recurrenceEventDeleted(accountId, recurrenceEventId);
    }
}
