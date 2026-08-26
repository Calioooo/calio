package com.calio.calendar.groupcalendar.sharing.event.service;

import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.service.EventQueryService;
import com.calio.calendar.groupcalendar.sharing.event.domain.PersonalEventGroupShare;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupcalendar.sharing.event.service.dto.PersonalEventGroupShareCommand;
import com.calio.calendar.groupcalendar.sharing.event.service.dto.PersonalEventGroupShareResult;
import com.calio.calendar.groupcalendar.sharing.event.service.dto.PersonalEventGroupShareTargetStatus;
import com.calio.calendar.groupcalendar.sharing.event.service.dto.PersonalEventGroupShareStatusResponse;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    public PersonalEventGroupShareResult share(
            Long accountId,
            PersonalEventGroupShareCommand command
    ) {
        List<Event> events = eventsToShare(accountId, command);
        List<Long> groupSpaceIds = new LinkedHashSet<>(command.groupSpaceIds()).stream().toList();
        if (groupSpaceIds.isEmpty()) {
            throw new CalioException(ErrorCode.VALIDATION_FAILED);
        }
        if ((long) events.size() * groupSpaceIds.size() > 2_000) {
            throw new CalioException(ErrorCode.VALIDATION_FAILED);
        }
        Map<Long, GroupMember> memberships = membershipQueryService.listActiveMemberships(accountId, groupSpaceIds)
                .stream().collect(Collectors.toMap(member -> member.getGroupSpace().getId(), Function.identity()));
        Set<String> existingPairs = shareQueryService.listSharesForEventsAndGroupSpaces(
                        events.stream().map(Event::getId).toList(), groupSpaceIds
                ).stream()
                .map(share -> pairKey(share.getEvent().getId(), share.getGroupSpace().getId()))
                .collect(Collectors.toSet());
        List<PersonalEventGroupShareResult.TargetResult> results = groupSpaceIds.stream()
                .map(groupSpaceId -> shareToGroup(events, groupSpaceId, memberships.get(groupSpaceId), existingPairs))
                .toList();
        return new PersonalEventGroupShareResult(results);
    }

    private PersonalEventGroupShareResult.TargetResult shareToGroup(
            List<Event> events, Long groupSpaceId, GroupMember membership, Set<String> existingPairs
    ) {
        if (membership == null) {
            return new PersonalEventGroupShareResult.TargetResult(
                    groupSpaceId, PersonalEventGroupShareTargetStatus.NOT_ELIGIBLE
            );
        }
        boolean created = false;
        for (Event event : events) {
            if (!existingPairs.contains(pairKey(event.getId(), groupSpaceId))) {
                shareCommandService.createShare(new PersonalEventGroupShare(event, membership.getGroupSpace()));
                created = true;
            }
        }
        return new PersonalEventGroupShareResult.TargetResult(
                groupSpaceId,
                created ? PersonalEventGroupShareTargetStatus.SHARED
                        : PersonalEventGroupShareTargetStatus.ALREADY_SHARED
        );
    }

    private String pairKey(Long eventId, Long groupSpaceId) {
        return eventId + ":" + groupSpaceId;
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
    public List<PersonalEventGroupShareStatusResponse> listStatuses(Long accountId, Long eventId) {
        Event event = eventQueryService.getPersonalOneOffEvent(accountId, eventId);
        return shareQueryService.listSharesForEvent(event.getId()).stream()
                .map(share -> new PersonalEventGroupShareStatusResponse(
                        share.getGroupSpace().getId(), share.getGroupSpace().getName(), share.isAnonymous()
                )).toList();
    }

    @Transactional
    public void changeAnonymous(Long accountId, Long groupSpaceId, Long eventId, boolean anonymous) {
        membershipQueryService.getActiveMembership(groupSpaceId, accountId);
        PersonalEventGroupShare share = shareQueryService.getShareIfExists(eventId, groupSpaceId)
                .orElseThrow(() -> new CalioException(ErrorCode.EVENT_NOT_FOUND));
        requireSourceOwner(accountId, share);
        shareCommandService.changeAnonymous(share, anonymous);
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
