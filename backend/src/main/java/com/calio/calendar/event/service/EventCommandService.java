package com.calio.calendar.event.service;

import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.controller.dto.UpdateEventRequest;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.tag.domain.Tag;
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

    public Event findEventForUpdate(Long accountId, Long eventId) {
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
}
