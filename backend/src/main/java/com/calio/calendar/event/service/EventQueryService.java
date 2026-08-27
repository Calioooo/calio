package com.calio.calendar.event.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EventQueryService {

    private final EventRepository eventRepository;

    public EventQueryService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public Event getEvent(Long accountId, Long eventId) {
        return eventRepository.findByIdAndAccount_Id(eventId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.EVENT_NOT_FOUND));
    }

    public Event getEventForShareManagement(Long accountId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new CalioException(ErrorCode.EVENT_NOT_FOUND));
        if (!event.getAccount().getId().equals(accountId)) {
            throw new CalioException(ErrorCode.PERSONAL_SCHEDULE_SHARE_FORBIDDEN);
        }
        return event;
    }

    public List<Event> listShareableEvents(Long accountId, List<Long> eventIds) {
        return eventRepository.findAllShareableByIdsAndAccountId(eventIds, accountId);
    }

    public List<Event> listEvents(Long accountId, Instant from, Instant to) {
        return eventRepository.findNormalEvents(accountId, from, to);
    }
}
