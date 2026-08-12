package com.calio.calendar.recurrence.service;

import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.tag.domain.Tag;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RecurrenceEventCommandService {

    private final RecurrenceEventRepository recurrenceEventRepository;
    private final EventRepository eventRepository;
    private final RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;

    public RecurrenceEventCommandService(
            RecurrenceEventRepository recurrenceEventRepository,
            EventRepository eventRepository,
            RecurrenceEventOverrideRepository recurrenceEventOverrideRepository
    ) {
        this.recurrenceEventRepository = recurrenceEventRepository;
        this.eventRepository = eventRepository;
        this.recurrenceEventOverrideRepository = recurrenceEventOverrideRepository;
    }

    public RecurrenceEvent createRecurrenceEvent(RecurrenceEvent recurrenceEvent) {
        return recurrenceEventRepository.save(recurrenceEvent);
    }

    public RecurrenceEvent findRecurrenceEventForUpdate(Long accountId, Long recurrenceId) {
        return recurrenceEventRepository.findByIdAndAccountIdForUpdate(recurrenceId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
    }

    public void updateRecurrenceEvent(
            RecurrenceEvent recurrenceEvent,
            UpdateRecurrenceEventRequest request,
            RecurrenceSchedule schedule,
            List<String> recurrenceRules,
            Tag tag
    ) {
        recurrenceEvent.update(request.title(), request.description(), schedule, recurrenceRules, tag);
        recurrenceEventRepository.flush();
    }

    public RecurrenceEventOverride updateRecurrenceOccurrence(RecurrenceEventOverride override) {
        return recurrenceEventOverrideRepository.saveAndFlush(override);
    }

    public void deleteRecurrenceEvent(Long recurrenceId) {
        recurrenceEventOverrideRepository.deleteAllByRecurrenceEventIds(List.of(recurrenceId));
        eventRepository.deleteAllByRecurrenceEventIds(List.of(recurrenceId));
        recurrenceEventRepository.deleteAllByIds(List.of(recurrenceId));
    }

    public void deleteRecurrenceOccurrence(RecurrenceEventOverride override) {
        recurrenceEventOverrideRepository.saveAndFlush(override);
    }
}
