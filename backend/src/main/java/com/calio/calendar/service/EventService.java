package com.calio.calendar.service;

import com.calio.calendar.controller.dto.CreateEventRequest;
import com.calio.calendar.controller.dto.EventResponse;
import com.calio.calendar.exception.EventNotFoundException;
import com.calio.calendar.exception.InvalidTimeRangeException;
import com.calio.calendar.repository.EventRepository;
import com.calio.calendar.repository.entity.Event;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        validateCreateTimeRange(request.startAt(), request.endAt());

        Event event = new Event(
                request.title(),
                request.description(),
                request.startAt(),
                request.endAt()
        );

        return EventResponse.from(eventRepository.save(event));
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long eventId) {
        return EventResponse.from(findEvent(eventId));
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listEvents(Instant from, Instant to) {
        validateListTimeRange(from, to);

        return eventRepository.findAllByStartAtBetweenOrderByStartAtAsc(from, to)
                .stream()
                .map(EventResponse::from)
                .toList();
    }

    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    private void validateCreateTimeRange(Instant startAt, Instant endAt) {
        if (!startAt.isBefore(endAt)) {
            throw new InvalidTimeRangeException("startAt must be earlier than endAt");
        }
    }

    private void validateListTimeRange(Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new InvalidTimeRangeException("from must be earlier than or equal to to");
        }
    }
}
