package com.calio.calendar.groupcalendar.recurrence.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceEvent;
import com.calio.calendar.groupcalendar.recurrence.repository.GroupCalendarRecurrenceEventRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GroupCalendarRecurrenceQueryService {

    private final GroupCalendarRecurrenceEventRepository recurrenceRepository;

    public GroupCalendarRecurrenceQueryService(GroupCalendarRecurrenceEventRepository recurrenceRepository) {
        this.recurrenceRepository = recurrenceRepository;
    }

    public GroupCalendarRecurrenceEvent getRecurrenceEvent(Long groupSpaceId, Long recurrenceId) {
        return recurrenceRepository.findByIdAndGroupSpace_Id(recurrenceId, groupSpaceId)
                .orElseThrow(() -> new CalioException(ErrorCode.GROUP_RECURRENCE_EVENT_NOT_FOUND));
    }

    public List<GroupCalendarRecurrenceEvent> listExpansionCandidates(Long groupSpaceId, Instant to) {
        return recurrenceRepository.findByGroupSpaceIdAndFirstOccurrenceStartAtBefore(groupSpaceId, to);
    }
}
