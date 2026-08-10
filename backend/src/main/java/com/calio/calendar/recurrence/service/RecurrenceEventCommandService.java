package com.calio.calendar.recurrence.service;

import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceOccurrenceRequest;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.tag.domain.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RecurrenceEventCommandService {

    private final RecurrenceEventRepository recurrenceEventRepository;
    private final EventRepository eventRepository;
    private final RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;
    private final Rfc5545RecurrenceEngine recurrenceEngine;

    public RecurrenceEventCommandService(
            RecurrenceEventRepository recurrenceEventRepository,
            EventRepository eventRepository,
            RecurrenceEventOverrideRepository recurrenceEventOverrideRepository,
            Rfc5545RecurrenceEngine recurrenceEngine
    ) {
        this.recurrenceEventRepository = recurrenceEventRepository;
        this.eventRepository = eventRepository;
        this.recurrenceEventOverrideRepository = recurrenceEventOverrideRepository;
        this.recurrenceEngine = recurrenceEngine;
    }

    public RecurrenceEvent createRecurrenceEvent(RecurrenceEvent recurrenceEvent) {
        return recurrenceEventRepository.save(recurrenceEvent);
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

    public RecurrenceEventOverride updateRecurrenceOccurrence(
            RecurrenceEvent recurrenceEvent,
            UpdateRecurrenceOccurrenceRequest request,
            CanonicalSchedule schedule
    ) {
        Optional<RecurrenceEventOverride> existingOverride = recurrenceEventOverrideRepository
                .findByRecurrenceEvent_IdAndOriginStartAt(recurrenceEvent.getId(), request.originStartAt());
        if (existingOverride.isEmpty() && !recurrenceContainsOrigin(recurrenceEvent, request.originStartAt())) {
            throw new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
        }
        RecurrenceEventOverride override = existingOverride.orElseGet(() -> RecurrenceEventOverride.active(
                recurrenceEvent,
                request.originStartAt(),
                request.title(),
                request.description(),
                schedule
        ));
        if (existingOverride.isPresent()) {
            override.activate(request.title(), request.description(), schedule);
        }
        return recurrenceEventOverrideRepository.saveAndFlush(override);
    }

    public void deleteRecurrenceEvent(RecurrenceEvent recurrenceEvent) {
        Long recurrenceId = recurrenceEvent.getId();
        recurrenceEventOverrideRepository.deleteAllByRecurrenceEventIds(List.of(recurrenceId));
        eventRepository.deleteAllByRecurrenceEventIds(List.of(recurrenceId));
        recurrenceEventRepository.delete(recurrenceEvent);
    }

    public void deleteRecurrenceOccurrence(RecurrenceEventOverride override) {
        recurrenceEventOverrideRepository.saveAndFlush(override);
    }

    private boolean recurrenceContainsOrigin(RecurrenceEvent recurrenceEvent, Instant originStartAt) {
        return recurrenceEngine.containsOrigin(
                RecurrenceSchedule.from(recurrenceEvent),
                recurrenceEvent.getRecurrenceRules(),
                originStartAt
        );
    }
}
