package com.calio.calendar.groupcalendar.recurrence.service;

import com.calio.calendar.groupcalendar.recurrence.repository.GroupCalendarRecurrenceEventRepository;
import com.calio.calendar.groupcalendar.recurrence.repository.GroupCalendarRecurrenceOverrideRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceEvent;
import com.calio.calendar.tag.domain.Tag;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GroupCalendarRecurrenceCommandService {

    private final GroupCalendarRecurrenceEventRepository recurrenceRepository;
    private final GroupCalendarRecurrenceOverrideRepository overrideRepository;

    public GroupCalendarRecurrenceCommandService(
            GroupCalendarRecurrenceEventRepository recurrenceRepository,
            GroupCalendarRecurrenceOverrideRepository overrideRepository
    ) {
        this.recurrenceRepository = recurrenceRepository;
        this.overrideRepository = overrideRepository;
    }

    public void deleteAllInGroupSpace(Long groupSpaceId) {
        overrideRepository.deleteAllByGroupSpaceId(groupSpaceId);
        recurrenceRepository.deleteAllByGroupSpace_Id(groupSpaceId);
    }

    public void deleteAllCreatedByMember(Long groupSpaceId, Long accountId) {
        overrideRepository.deleteAllByGroupSpaceIdAndCreatedById(groupSpaceId, accountId);
        recurrenceRepository.deleteAllByGroupSpace_IdAndCreatedBy_Id(groupSpaceId, accountId);
    }

    public void changeTagForRecurrenceEvents(Tag sourceTag, Tag fallbackTag) {
        recurrenceRepository.reassignAllByTag(sourceTag, fallbackTag);
    }

    public GroupCalendarRecurrenceEvent createRecurrenceEvent(GroupCalendarRecurrenceEvent event) {
        return recurrenceRepository.save(event);
    }

    public GroupCalendarRecurrenceEvent lockRecurrenceEvent(Long groupSpaceId, Long recurrenceId) {
        return recurrenceRepository.findByIdAndGroupSpaceIdForUpdate(recurrenceId, groupSpaceId)
                .orElseThrow(() -> new CalioException(ErrorCode.GROUP_RECURRENCE_EVENT_NOT_FOUND));
    }

    public void deleteRecurrenceEvent(GroupCalendarRecurrenceEvent event) {
        overrideRepository.deleteAllByRecurrenceEventId(event.getId());
        recurrenceRepository.delete(event);
    }
}
