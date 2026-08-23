package com.calio.calendar.groupcalendar.sharing.event.service;

import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.service.EventQueryService;
import com.calio.calendar.groupcalendar.sharing.event.domain.PersonalEventGroupShare;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupcalendar.sharing.event.service.dto.PersonalEventGroupShareCommand;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PersonalEventGroupShareService {

    private final EventQueryService eventQueryService;
    private final GroupMembershipQueryService membershipQueryService;
    private final PersonalEventGroupShareCommandService shareCommandService;

    public PersonalEventGroupShareService(
            EventQueryService eventQueryService,
            GroupMembershipQueryService membershipQueryService,
            PersonalEventGroupShareCommandService shareCommandService
    ) {
        this.eventQueryService = eventQueryService;
        this.membershipQueryService = membershipQueryService;
        this.shareCommandService = shareCommandService;
    }

    @Transactional
    public void share(
            Long accountId,
            Long groupSpaceId,
            PersonalEventGroupShareCommand command
    ) {
        GroupMember membership = membershipQueryService.getActiveMembership(groupSpaceId, accountId);
        shareEvents(accountId, command, membership);
    }

    private void shareEvents(
            Long accountId,
            PersonalEventGroupShareCommand command,
            GroupMember membership
    ) {
        eventsToShare(accountId, command)
                .forEach(event -> shareCommandService.createShare(
                        new PersonalEventGroupShare(event, membership.getGroupSpace())
                ));
    }

    private List<Event> eventsToShare(Long accountId, PersonalEventGroupShareCommand command) {
        if (!command.selectionEnabled()) {
            return eventQueryService.listPersonalOneOffEvents(accountId);
        }
        if (command.eventIds().isEmpty()) {
            throw new CalioException(ErrorCode.VALIDATION_FAILED);
        }
        return selectedEvents(accountId, command.eventIds());
    }

    private List<Event> selectedEvents(Long accountId, List<Long> eventIds) {
        Set<Long> distinctEventIds = new LinkedHashSet<>(eventIds);
        return distinctEventIds.stream()
                .map(eventId -> eventQueryService.getEvent(accountId, eventId))
                .toList();
    }
}
