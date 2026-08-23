package com.calio.calendar.groupcalendar.sharing.recurrence.service;

import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareScope;
import com.calio.calendar.groupcalendar.sharing.recurrence.service.dto.PersonalRecurrenceGroupShareCommand;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.service.RecurrenceEventQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PersonalRecurrenceGroupShareService {

    private final RecurrenceEventQueryService recurrenceEventQueryService;
    private final GroupMembershipQueryService membershipQueryService;
    private final PersonalRecurrenceGroupShareCommandService shareCommandService;

    public PersonalRecurrenceGroupShareService(
            RecurrenceEventQueryService recurrenceEventQueryService,
            GroupMembershipQueryService membershipQueryService,
            PersonalRecurrenceGroupShareCommandService shareCommandService
    ) {
        this.recurrenceEventQueryService = recurrenceEventQueryService;
        this.membershipQueryService = membershipQueryService;
        this.shareCommandService = shareCommandService;
    }

    @Transactional
    public void shareWholeSeries(
            Long accountId,
            Long groupSpaceId,
            PersonalRecurrenceGroupShareCommand command
    ) {
        GroupMember membership = membershipQueryService.getActiveMembership(groupSpaceId, accountId);
        RecurrenceEvent recurrenceEvent = recurrenceEventQueryService.getRecurrenceEvent(
                accountId,
                command.recurrenceId()
        );
        PersonalRecurrenceGroupShare share = new PersonalRecurrenceGroupShare(
                recurrenceEvent,
                membership.getGroupSpace(),
                PersonalRecurrenceGroupShareScope.WHOLE_SERIES
        );
        shareCommandService.createShare(share);
    }
}
