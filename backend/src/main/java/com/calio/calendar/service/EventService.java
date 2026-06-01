package com.calio.calendar.service;

import com.calio.calendar.controller.dto.CreateEventRequest;
import com.calio.calendar.controller.dto.EventResponse;
import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
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
        validateEventTimeRange(request.startAt(), request.endAt());
        Event event = eventRepository.save(request.toEntity());
        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long eventId) {
        Event event = findEvent(eventId);
        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listEvents(Instant from, Instant to) {
        validateListTimeRange(from, to);
        return eventRepository.findByStartAtBetweenOrderByStartAtAsc(from, to)
                .stream()
                .map(EventResponse::from)
                .toList();
    }

    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new CalioException(ErrorCode.EVENT_NOT_FOUND));
    }

    private void validateEventTimeRange(Instant startAt, Instant endAt) {
        if (startAt.isBefore(endAt)) {
            return;
        }

        throw new CalioException(ErrorCode.INVALID_TIME_RANGE);
    }

    private void validateListTimeRange(Instant from, Instant to) {
        if (!from.isAfter(to)) {
            return;
        }

        throw new CalioException(ErrorCode.INVALID_TIME_RANGE);
    }
}
