package com.calio.calendar.groupcalendar.event.service;

import com.calio.calendar.groupcalendar.event.repository.GroupCalendarEventRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupcalendar.event.domain.GroupCalendarEvent;
import com.calio.calendar.tag.domain.Tag;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GroupCalendarEventCommandService {

    private final GroupCalendarEventRepository eventRepository;

    public GroupCalendarEventCommandService(GroupCalendarEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public void deleteAllByGroupSpaceId(Long groupSpaceId) {
        eventRepository.deleteAllByGroupSpaceId(groupSpaceId);
    }

    public GroupCalendarEvent createEvent(GroupCalendarEvent event) {
        return eventRepository.save(event);
    }

    public GroupCalendarEvent lockEvent(Long groupSpaceId, Long eventId) {
        return eventRepository.findByIdAndGroupSpaceIdForUpdate(groupSpaceId, eventId)
                .orElseThrow(() -> new CalioException(ErrorCode.GROUP_EVENT_NOT_FOUND));
    }

    public void deleteEvent(GroupCalendarEvent event) {
        eventRepository.delete(event);
    }

    public void deleteAllByGroupSpaceIdAndCreatedById(Long groupSpaceId, Long accountId) {
        eventRepository.deleteAllByGroupSpaceIdAndCreatedById(groupSpaceId, accountId);
    }

    public void changeTagForEvents(Tag sourceTag, Tag fallbackTag) {
        eventRepository.reassignAllByTag(sourceTag, fallbackTag);
    }
}
