package com.calio.calendar.groupspace.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.groupinvitation.service.GroupInvitationCommandService;
import com.calio.calendar.groupspace.controller.dto.CreateGroupSpaceRequest;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceDetailResponse;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceListResponse;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceSummaryResponse;
import com.calio.calendar.groupspace.controller.dto.UpdateGroupSpaceRequest;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.domain.GroupSpaceFields;
import com.calio.calendar.tag.service.GroupTagService;
import com.calio.calendar.groupcalendar.event.service.GroupCalendarEventCommandService;
import com.calio.calendar.groupcalendar.recurrence.service.GroupCalendarRecurrenceCommandService;
import com.calio.calendar.groupcalendar.sharing.event.service.PersonalEventGroupShareCommandService;
import com.calio.calendar.groupcalendar.sharing.recurrence.service.PersonalRecurrenceGroupShareCommandService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupSpaceService {

    private final GroupSpaceQueryService queryService;
    private final GroupMembershipQueryService membershipQueryService;
    private final GroupMembershipCommandService membershipCommandService;
    private final AccountQueryService accountQueryService;
    private final GroupSpaceCommandService commandService;
    private final GroupInvitationCommandService invitationCommandService;
    private final PersonalEventGroupShareCommandService personalEventGroupShareCommandService;
    private final PersonalRecurrenceGroupShareCommandService personalRecurrenceGroupShareCommandService;
    private final GroupTagService groupTagService;
    private final GroupCalendarEventCommandService groupCalendarEventCommandService;
    private final GroupCalendarRecurrenceCommandService groupCalendarRecurrenceCommandService;
    private final Clock clock;

    @Autowired
    public GroupSpaceService(
            GroupSpaceQueryService queryService,
            GroupMembershipQueryService membershipQueryService,
            GroupMembershipCommandService membershipCommandService,
            AccountQueryService accountQueryService,
            GroupSpaceCommandService commandService,
            GroupInvitationCommandService invitationCommandService,
            PersonalEventGroupShareCommandService personalEventGroupShareCommandService,
            PersonalRecurrenceGroupShareCommandService personalRecurrenceGroupShareCommandService,
            GroupTagService groupTagService,
            GroupCalendarEventCommandService groupCalendarEventCommandService,
            GroupCalendarRecurrenceCommandService groupCalendarRecurrenceCommandService,
            Clock clock
    ) {
        this.queryService = queryService;
        this.membershipQueryService = membershipQueryService;
        this.membershipCommandService = membershipCommandService;
        this.accountQueryService = accountQueryService;
        this.commandService = commandService;
        this.invitationCommandService = invitationCommandService;
        this.personalEventGroupShareCommandService = personalEventGroupShareCommandService;
        this.personalRecurrenceGroupShareCommandService = personalRecurrenceGroupShareCommandService;
        this.groupTagService = groupTagService;
        this.groupCalendarEventCommandService = groupCalendarEventCommandService;
        this.groupCalendarRecurrenceCommandService = groupCalendarRecurrenceCommandService;
        this.clock = clock;
    }

    @Transactional
    public GroupSpaceDetailResponse create(Long accountId, CreateGroupSpaceRequest request) {
        ensureAccountExists(accountId);
        String name = GroupSpaceFields.normalizeName(request.name());
        String nickname = GroupSpaceFields.normalizeNickname(request.nickname());
        String emoji = GroupSpaceFields.canonicalizeEmoji(request.emoji());
        Instant now = clock.instant();

        GroupSpace groupSpace = commandService.create(accountId, name, emoji);
        groupTagService.createDefaultTag(groupSpace);
        GroupMember membership = commandService.createOwnerMembership(
                groupSpace,
                accountId,
                nickname,
                now
        );
        return GroupSpaceDetailResponse.from(groupSpace, membership, 1);
    }

    @Transactional(readOnly = true)
    public GroupSpaceListResponse list(Long accountId) {
        List<GroupSpaceSummaryResponse> groupSpaces = queryService
                .listActiveMemberships(accountId)
                .stream()
                .map(this::toSummary)
                .toList();
        return new GroupSpaceListResponse(groupSpaces);
    }

    @Transactional(readOnly = true)
    public GroupSpaceDetailResponse get(Long accountId, Long groupSpaceId) {
        GroupMember membership = findActiveMembership(groupSpaceId, accountId);
        return toDetail(membership);
    }

    @Transactional
    public GroupSpaceDetailResponse update(
            Long accountId,
            Long groupSpaceId,
            UpdateGroupSpaceRequest request
    ) {
        GroupSpace groupSpace = commandService.lockGroupSpace(groupSpaceId);
        GroupMember membership = membershipQueryService.getActiveMembership(groupSpaceId, accountId);
        requireOwner(groupSpace, membership);

        String name = GroupSpaceFields.normalizeName(request.name());
        String emoji = GroupSpaceFields.canonicalizeEmoji(request.emoji());
        commandService.update(groupSpace, name, emoji);
        return GroupSpaceDetailResponse.from(groupSpace, membership, activeMemberCount(groupSpaceId));
    }

    @Transactional
    public void delete(Long accountId, Long groupSpaceId) {
        GroupSpace groupSpace = commandService.lockGroupSpace(groupSpaceId);
        GroupMember membership = membershipQueryService.getActiveMembership(groupSpaceId, accountId);
        requireOwner(groupSpace, membership);

        membershipCommandService.lockMembers(groupSpaceId);
        cleanupGroupShares(groupSpaceId);
        groupCalendarEventCommandService.deleteAllByGroupSpaceId(groupSpaceId);
        groupCalendarRecurrenceCommandService.deleteAllInGroupSpace(groupSpaceId);
        invitationCommandService.deleteAllByGroupSpaceId(groupSpaceId);
        groupTagService.deleteAll(groupSpaceId);
        commandService.delete(groupSpace);
    }

    private void ensureAccountExists(Long accountId) {
        if (!accountQueryService.hasAccount(accountId)) {
            throw new CalioException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private GroupMember findActiveMembership(Long groupSpaceId, Long accountId) {
        return membershipQueryService.getActiveMembership(groupSpaceId, accountId);
    }

    private void requireOwner(GroupSpace groupSpace, GroupMember membership) {
        if (!groupSpace.getOwnerAccountId().equals(membership.getAccountId())) {
            throw new CalioException(ErrorCode.GROUP_OWNER_REQUIRED);
        }
    }

    private GroupSpaceSummaryResponse toSummary(GroupMember membership) {
        GroupSpace groupSpace = membership.getGroupSpace();
        return GroupSpaceSummaryResponse.from(
                groupSpace,
                membership,
                activeMemberCount(groupSpace.getId())
        );
    }

    private GroupSpaceDetailResponse toDetail(GroupMember membership) {
        GroupSpace groupSpace = membership.getGroupSpace();
        return GroupSpaceDetailResponse.from(
                groupSpace,
                membership,
                activeMemberCount(groupSpace.getId())
        );
    }

    private int activeMemberCount(Long groupSpaceId) {
        return queryService.getActiveMemberCount(groupSpaceId);
    }

    private void cleanupGroupShares(Long groupSpaceId) {
        personalEventGroupShareCommandService.deleteAllInGroupSpace(groupSpaceId);
        personalRecurrenceGroupShareCommandService.deleteAllInGroupSpace(groupSpaceId);
    }

    private static CalioException groupSpaceNotFound() {
        return new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND);
    }
}
