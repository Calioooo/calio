package com.calio.calendar.sharing.event.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.service.EventQueryService;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.sharing.controller.dto.GroupShareTargetResponse;
import com.calio.calendar.sharing.controller.dto.GroupShareTargetStatus;
import com.calio.calendar.sharing.controller.dto.GroupShareStatusResponse;
import com.calio.calendar.sharing.controller.dto.UpdateGroupShareAnonymousRequest;
import com.calio.calendar.sharing.event.controller.dto.CreateEventGroupSharesRequest;
import com.calio.calendar.sharing.event.controller.dto.CreateEventGroupSharesResponse;
import com.calio.calendar.sharing.event.controller.dto.EventGroupShareResultResponse;
import com.calio.calendar.sharing.event.domain.PersonalEventGroupShare;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    public CreateEventGroupSharesResponse create(Long accountId, CreateEventGroupSharesRequest request) {
        List<Long> eventIds = distinct(request.eventIds());
        List<Long> groupSpaceIds = distinct(request.groupSpaceIds());
        Map<Long, Event> eventsById = validateSources(accountId, eventIds);
        Map<Long, GroupSpace> activeGroupSpacesById = activeGroupSpaces(accountId, groupSpaceIds);
        Set<ShareKey> existingKeys = existingKeys(eventIds, groupSpaceIds);

        List<EventGroupShareResultResponse> results = eventIds.stream()
                .map(eventId -> EventGroupShareResultResponse.from(
                        eventId,
                        shareTargets(
                                eventsById.get(eventId),
                                groupSpaceIds,
                                activeGroupSpacesById,
                                existingKeys,
                                request.isAnonymous()
                        )
                ))
                .toList();
        return CreateEventGroupSharesResponse.from(results);
    }

    public List<GroupShareStatusResponse> list(Long eventId) {
        return shareQueryService.listByEventId(eventId).stream()
                .map(GroupShareStatusResponse::from)
                .toList();
    }

    @Transactional
    public GroupShareStatusResponse changeAnonymous(
            Long eventId,
            Long groupSpaceId,
            UpdateGroupShareAnonymousRequest request
    ) {
        PersonalEventGroupShare share = shareQueryService.getByEventIdAndGroupSpaceId(eventId, groupSpaceId);
        shareCommandService.changeAnonymous(share, request.isAnonymous());
        return GroupShareStatusResponse.from(share);
    }

    @Transactional
    public void remove(Long eventId, Long groupSpaceId) {
        PersonalEventGroupShare share = shareQueryService.getByEventIdAndGroupSpaceId(eventId, groupSpaceId);
        shareCommandService.delete(share);
    }

    private Map<Long, Event> validateSources(Long accountId, List<Long> eventIds) {
        Map<Long, Event> eventsById = eventQueryService.listShareableEvents(accountId, eventIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(Event::getId, event -> event));
        if (eventsById.size() != eventIds.size()) {
            throw new CalioException(ErrorCode.EVENT_NOT_FOUND);
        }
        return eventsById;
    }

    private Map<Long, GroupSpace> activeGroupSpaces(Long accountId, List<Long> groupSpaceIds) {
        return membershipQueryService.listActiveMemberships(accountId, groupSpaceIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        member -> member.getGroupSpace().getId(),
                        GroupMember::getGroupSpace
                ));
    }

    private Set<ShareKey> existingKeys(Collection<Long> eventIds, Collection<Long> groupSpaceIds) {
        return shareQueryService.listExistingShares(eventIds, groupSpaceIds)
                .stream()
                .map(share -> new ShareKey(share.getEvent().getId(), share.getGroupSpace().getId()))
                .collect(java.util.stream.Collectors.toSet());
    }

    private List<GroupShareTargetResponse> shareTargets(
            Event event,
            List<Long> groupSpaceIds,
            Map<Long, GroupSpace> activeGroupSpacesById,
            Set<ShareKey> existingKeys,
            boolean anonymous
    ) {
        return groupSpaceIds.stream()
                .map(groupSpaceId -> shareTarget(
                        event,
                        groupSpaceId,
                        activeGroupSpacesById.get(groupSpaceId),
                        existingKeys,
                        anonymous
                ))
                .toList();
    }

    private GroupShareTargetResponse shareTarget(
            Event event,
            Long groupSpaceId,
            GroupSpace groupSpace,
            Set<ShareKey> existingKeys,
            boolean anonymous
    ) {
        if (groupSpace == null) {
            return GroupShareTargetResponse.from(groupSpaceId, GroupShareTargetStatus.NOT_ELIGIBLE);
        }
        ShareKey key = new ShareKey(event.getId(), groupSpaceId);
        if (existingKeys.contains(key)) {
            return GroupShareTargetResponse.from(groupSpaceId, GroupShareTargetStatus.ALREADY_SHARED);
        }
        boolean inserted = shareCommandService.createIfAbsent(
                PersonalEventGroupShare.create(event, groupSpace, anonymous)
        );
        return GroupShareTargetResponse.from(
                groupSpaceId,
                inserted ? GroupShareTargetStatus.SHARED : GroupShareTargetStatus.ALREADY_SHARED
        );
    }

    private static List<Long> distinct(List<Long> ids) {
        return List.copyOf(new LinkedHashSet<>(ids));
    }

    private record ShareKey(Long eventId, Long groupSpaceId) {
    }
}
