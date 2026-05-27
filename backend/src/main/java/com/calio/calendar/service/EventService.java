package com.calio.calendar.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.calio.calendar.common.exception.CalendarException;
import com.calio.calendar.common.exception.ErrorCode;
import com.calio.calendar.controller.dto.CreateEventRequest;
import com.calio.calendar.controller.dto.EventResponse;
import com.calio.calendar.repository.EventRepository;
import com.calio.calendar.repository.entity.Event;

@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        validateEventTimeRange(request.startAt(), request.endAt());

        Event event = new Event(
                request.title(),
                request.description(),
                request.startAt(),
                request.endAt()
        );

        return EventResponse.from(eventRepository.saveAndFlush(event));
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new CalendarException(
                        ErrorCode.EVENT_NOT_FOUND,
                        "Event not found for id " + eventId
                ));

        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listEvents(Instant from, Instant to) {
        validateQueryTimeRange(from, to);

        return eventRepository.findByStartAtBetweenOrderByStartAtAsc(from, to)
                .stream()
                .map(EventResponse::from)
                .toList();
    }

    private void validateEventTimeRange(Instant startAt, Instant endAt) {
        if (!startAt.isBefore(endAt)) {
            throw new CalendarException(
                    ErrorCode.INVALID_TIME_RANGE,
                    "startAt must be earlier than endAt"
            );
        }
    }

    private void validateQueryTimeRange(Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new CalendarException(
                    ErrorCode.INVALID_TIME_RANGE,
                    "from must be earlier than or equal to to"
            );
        }
    }
}
