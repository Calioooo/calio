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
    private final PersonalEventGroupShareQueryService shareQueryService;
    private final PersonalEventGroupShareCommandService shareCommandService;

    public PersonalEventGroupShareService(
            EventQueryService eventQueryService,
            GroupMembershipQueryService membershipQueryService,
            PersonalEventGroupShareQueryService shareQueryService,
            PersonalEventGroupShareCommandService shareCommandService
    ) {
        this.eventQueryService = eventQueryService;
        this.membershipQueryService = membershipQueryService;
        this.shareQueryService = shareQueryService;
        this.shareCommandService = shareCommandService;
    }

    @Transactional
    public void share(
            Long accountId,
            PersonalEventGroupShareCommand command
    ) {
        List<GroupMember> memberships = activeMemberships(accountId, command.groupSpaceIds());
        shareEvents(accountId, command, memberships);
    }

    private void shareEvents(
            Long accountId,
            PersonalEventGroupShareCommand command,
            List<GroupMember> memberships
    ) {
        eventsToShare(accountId, command)
                .forEach(event -> memberships.forEach(membership -> shareCommandService.createShare(
                        new PersonalEventGroupShare(event, membership.getGroupSpace())
                )));
    }

    private List<GroupMember> activeMemberships(Long accountId, List<Long> groupSpaceIds) {
        if (groupSpaceIds.isEmpty()) {
            throw new CalioException(ErrorCode.VALIDATION_FAILED);
        }
        return new LinkedHashSet<>(groupSpaceIds).stream()
                .map(groupSpaceId -> membershipQueryService.getActiveMembership(groupSpaceId, accountId))
                .toList();
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
                .map(eventId -> eventQueryService.getPersonalOneOffEvent(accountId, eventId))
                .toList();
    }

    @Transactional
    public void remove(Long accountId, Long groupSpaceId, Long eventId) {
        membershipQueryService.getActiveMembership(groupSpaceId, accountId);
        PersonalEventGroupShare share = shareQueryService.getShareIfExists(eventId, groupSpaceId)
                .orElseThrow(() -> new CalioException(ErrorCode.EVENT_NOT_FOUND));
        requireSourceOwner(accountId, share);
        shareCommandService.deleteShare(share);
    }

    private void requireSourceOwner(Long accountId, PersonalEventGroupShare share) {
        if (!share.getEvent().getAccount().getId().equals(accountId)) {
            throw new CalioException(ErrorCode.PERSONAL_EVENT_GROUP_SHARE_FORBIDDEN);
        }
    }

}
