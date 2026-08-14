package com.calio.calendar.event.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.integration.mapping.repository.GoogleCalendarEventMappingRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EventQueryService {

    private final EventRepository eventRepository;
    private final GoogleCalendarEventMappingRepository googleCalendarEventMappingRepository;

    public EventQueryService(
            EventRepository eventRepository,
            GoogleCalendarEventMappingRepository googleCalendarEventMappingRepository
    ) {
        this.eventRepository = eventRepository;
        this.googleCalendarEventMappingRepository = googleCalendarEventMappingRepository;
    }

    public Event findEvent(Long accountId, Long eventId) {
        return eventRepository.findByIdAndAccount_Id(eventId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.EVENT_NOT_FOUND));
    }

    public boolean hasExternalEventMapping(Long accountId, Long eventId) {
        return googleCalendarEventMappingRepository
                .existsByEvent_IdAndIntegration_AccountId(eventId, accountId);
    }

    public List<Event> listEvents(Long accountId, Instant from, Instant to) {
        return eventRepository.findNormalEvents(accountId, from, to);
    }
}
