package com.calio.calendar.event.service;

import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.tag.domain.Tag;
import org.springframework.stereotype.Service;

@Service
public class EventCommandService {

    private final EventRepository eventRepository;

    public EventCommandService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public void changeTagForTargetEvents(Long accountId, Tag sourceTag, Tag targetTag) {
        eventRepository.reassignAllByTagAndAccountId(sourceTag, targetTag, accountId);
    }
}
