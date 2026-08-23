package com.calio.calendar.groupcalendar.sharing.service;

import com.calio.calendar.groupcalendar.sharing.event.service.PersonalEventGroupShareCommandService;
import com.calio.calendar.groupcalendar.sharing.recurrence.service.PersonalRecurrenceGroupShareCommandService;
import com.calio.calendar.groupspace.service.GroupScheduleShareCleanupPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PersonalScheduleGroupShareCleanupAdapter implements GroupScheduleShareCleanupPort {

    private final PersonalEventGroupShareCommandService personalEventGroupShareCommandService;
    private final PersonalRecurrenceGroupShareCommandService personalRecurrenceGroupShareCommandService;

    public PersonalScheduleGroupShareCleanupAdapter(
            PersonalEventGroupShareCommandService personalEventGroupShareCommandService,
            PersonalRecurrenceGroupShareCommandService personalRecurrenceGroupShareCommandService
    ) {
        this.personalEventGroupShareCommandService = personalEventGroupShareCommandService;
        this.personalRecurrenceGroupShareCommandService = personalRecurrenceGroupShareCommandService;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cleanupMemberShares(Long groupSpaceId, Long accountId) {
        personalEventGroupShareCommandService.deleteAllForMemberInGroupSpace(groupSpaceId, accountId);
        personalRecurrenceGroupShareCommandService.deleteAllForMemberInGroupSpace(groupSpaceId, accountId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cleanupGroupShares(Long groupSpaceId) {
    }
}
