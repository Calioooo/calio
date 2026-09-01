package com.calio.calendar.groupcalendar.event.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupcalendar.event.domain.GroupCalendarEvent;
import com.calio.calendar.groupcalendar.event.repository.GroupCalendarEventRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GroupCalendarEventQueryService {

    private final GroupCalendarEventRepository eventRepository;

    public GroupCalendarEventQueryService(GroupCalendarEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public GroupCalendarEvent getEvent(Long groupSpaceId, Long eventId) {
        return eventRepository.findByIdAndGroupSpace_Id(eventId, groupSpaceId)
                .orElseThrow(() -> new CalioException(ErrorCode.GROUP_EVENT_NOT_FOUND));
    }

    public List<GroupCalendarEvent> listOverlappingEvents(Long groupSpaceId, Instant from, Instant to) {
        return eventRepository
                .findByGroupSpace_IdAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(groupSpaceId, to, from);
    }
}
