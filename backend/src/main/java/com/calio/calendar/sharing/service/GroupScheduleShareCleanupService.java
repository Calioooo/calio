package com.calio.calendar.sharing.service;

import com.calio.calendar.groupspace.service.GroupScheduleShareCleanupPort;
import com.calio.calendar.sharing.event.service.PersonalEventGroupShareCommandService;
import com.calio.calendar.sharing.recurrence.service.PersonalRecurrenceGroupShareCommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupScheduleShareCleanupService implements GroupScheduleShareCleanupPort {

    private final PersonalEventGroupShareCommandService eventShareCommandService;
    private final PersonalRecurrenceGroupShareCommandService recurrenceShareCommandService;

    public GroupScheduleShareCleanupService(
            PersonalEventGroupShareCommandService eventShareCommandService,
            PersonalRecurrenceGroupShareCommandService recurrenceShareCommandService
    ) {
        this.eventShareCommandService = eventShareCommandService;
        this.recurrenceShareCommandService = recurrenceShareCommandService;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cleanupMemberShares(Long groupSpaceId, Long memberId) {
        eventShareCommandService.deleteAllForMemberInGroupSpace(groupSpaceId, memberId);
        recurrenceShareCommandService.deleteAllForMemberInGroupSpace(groupSpaceId, memberId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cleanupGroupShares(Long groupSpaceId) {
        eventShareCommandService.deleteAllForGroupSpace(groupSpaceId);
        recurrenceShareCommandService.deleteAllForGroupSpace(groupSpaceId);
    }
}
