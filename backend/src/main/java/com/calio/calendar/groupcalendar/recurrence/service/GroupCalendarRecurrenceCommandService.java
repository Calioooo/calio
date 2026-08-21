package com.calio.calendar.groupcalendar.recurrence.service;

import com.calio.calendar.groupcalendar.recurrence.repository.GroupCalendarRecurrenceEventRepository;
import com.calio.calendar.tag.domain.Tag;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GroupCalendarRecurrenceCommandService {

    private final GroupCalendarRecurrenceEventRepository recurrenceRepository;

    public GroupCalendarRecurrenceCommandService(GroupCalendarRecurrenceEventRepository recurrenceRepository) {
        this.recurrenceRepository = recurrenceRepository;
    }

    public void deleteAllByGroupSpaceId(Long groupSpaceId) {
        recurrenceRepository.deleteAllByGroupSpace_Id(groupSpaceId);
    }

    public void deleteAllByGroupSpaceIdAndCreatedById(Long groupSpaceId, Long accountId) {
        recurrenceRepository.deleteAllByGroupSpace_IdAndCreatedBy_Id(groupSpaceId, accountId);
    }

    public void changeTagForRecurrenceEvents(Tag sourceTag, Tag fallbackTag) {
        recurrenceRepository.reassignAllByTag(sourceTag, fallbackTag);
    }
}
