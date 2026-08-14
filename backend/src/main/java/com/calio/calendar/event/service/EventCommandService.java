package com.calio.calendar.event.service;

import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.controller.dto.UpdateEventRequest;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.tag.domain.Tag;
import java.util.Collection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EventCommandService {

    private final EventRepository eventRepository;

    public EventCommandService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public Event createEvent(Event event) {
        return eventRepository.save(event);
    }

    public Event lockEvent(Long accountId, Long eventId) {
        return eventRepository.findByIdAndAccountIdForUpdate(eventId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.EVENT_NOT_FOUND));
    }

    public void updateEvent(Event event, UpdateEventRequest request, CanonicalSchedule schedule, Tag tag) {
        event.replace(
                request.title(),
                request.description(),
                schedule.startAt(),
                schedule.endAt(),
                schedule.allDay(),
                schedule.timeZone(),
                tag
        );
        eventRepository.flush();
    }

    public void updateImportantEvent(Event event, boolean importantEvent) {
        event.changeImportantEvent(importantEvent);
        eventRepository.flush();
    }

    public void deleteEvent(Event event) {
        eventRepository.delete(event);
    }

    public void deleteEventsByIds(Collection<Long> eventIds) {
        if (!eventIds.isEmpty()) {
            eventRepository.deleteAllByIds(eventIds.stream().toList());
        }
    }

    public void deleteEventsByRecurrenceEventIds(Collection<Long> recurrenceEventIds) {
        if (!recurrenceEventIds.isEmpty()) {
            eventRepository.deleteAllByRecurrenceEventIds(recurrenceEventIds);
        }
    }

    public void changeTagForTargetEvents(Long accountId, Tag sourceTag, Tag targetTag) {
        eventRepository.reassignAllByTagAndAccountId(sourceTag, targetTag, accountId);
    }
}
