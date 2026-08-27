package com.calio.calendar.sharing.recurrence.service;

import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.service.RecurrenceEventQueryService;
import com.calio.calendar.sharing.controller.dto.GroupShareTargetResponse;
import com.calio.calendar.sharing.controller.dto.GroupShareTargetStatus;
import com.calio.calendar.sharing.recurrence.controller.dto.CreateRecurrenceGroupSharesRequest;
import com.calio.calendar.sharing.recurrence.controller.dto.CreateRecurrenceGroupSharesResponse;
import com.calio.calendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PersonalRecurrenceGroupShareService {

    private final RecurrenceEventQueryService recurrenceEventQueryService;
    private final GroupMembershipQueryService membershipQueryService;
    private final PersonalRecurrenceGroupShareQueryService shareQueryService;
    private final PersonalRecurrenceGroupShareCommandService shareCommandService;

    public PersonalRecurrenceGroupShareService(
            RecurrenceEventQueryService recurrenceEventQueryService,
            GroupMembershipQueryService membershipQueryService,
            PersonalRecurrenceGroupShareQueryService shareQueryService,
            PersonalRecurrenceGroupShareCommandService shareCommandService
    ) {
        this.recurrenceEventQueryService = recurrenceEventQueryService;
        this.membershipQueryService = membershipQueryService;
        this.shareQueryService = shareQueryService;
        this.shareCommandService = shareCommandService;
    }

    @Transactional
    public CreateRecurrenceGroupSharesResponse create(
            Long accountId,
            Long recurrenceId,
            CreateRecurrenceGroupSharesRequest request
    ) {
        RecurrenceEvent recurrenceEvent = recurrenceEventQueryService.getRecurrenceEvent(accountId, recurrenceId);
        List<Long> groupSpaceIds = distinct(request.groupSpaceIds());
        Map<Long, GroupSpace> activeGroupSpacesById = activeGroupSpaces(accountId, groupSpaceIds);
        Set<Long> existingGroupSpaceIds = shareQueryService
                .listExistingShares(List.of(recurrenceId), groupSpaceIds)
                .stream()
                .map(share -> share.getGroupSpace().getId())
                .collect(java.util.stream.Collectors.toSet());

        List<GroupShareTargetResponse> targets = groupSpaceIds.stream()
                .map(groupSpaceId -> shareTarget(
                        recurrenceEvent,
                        groupSpaceId,
                        activeGroupSpacesById.get(groupSpaceId),
                        existingGroupSpaceIds,
                        request.isAnonymous()
                ))
                .toList();
        return new CreateRecurrenceGroupSharesResponse(recurrenceId, targets);
    }

    private Map<Long, GroupSpace> activeGroupSpaces(Long accountId, List<Long> groupSpaceIds) {
        return membershipQueryService.listActiveMemberships(accountId, groupSpaceIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        member -> member.getGroupSpace().getId(),
                        GroupMember::getGroupSpace
                ));
    }

    private GroupShareTargetResponse shareTarget(
            RecurrenceEvent recurrenceEvent,
            Long groupSpaceId,
            GroupSpace groupSpace,
            Set<Long> existingGroupSpaceIds,
            boolean anonymous
    ) {
        if (groupSpace == null) {
            return new GroupShareTargetResponse(groupSpaceId, GroupShareTargetStatus.NOT_ELIGIBLE);
        }
        if (existingGroupSpaceIds.contains(groupSpaceId)) {
            return new GroupShareTargetResponse(groupSpaceId, GroupShareTargetStatus.ALREADY_SHARED);
        }
        boolean inserted = shareCommandService.createIfAbsent(
                PersonalRecurrenceGroupShare.create(recurrenceEvent, groupSpace, anonymous)
        );
        return new GroupShareTargetResponse(
                groupSpaceId,
                inserted ? GroupShareTargetStatus.SHARED : GroupShareTargetStatus.ALREADY_SHARED
        );
    }

    private static List<Long> distinct(List<Long> ids) {
        return List.copyOf(new LinkedHashSet<>(ids));
    }
}
