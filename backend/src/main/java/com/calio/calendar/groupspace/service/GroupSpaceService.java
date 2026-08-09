package com.calio.calendar.groupspace.service;

import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupinvitation.service.GroupInvitationCommandService;
import com.calio.calendar.groupspace.controller.dto.CreateGroupSpaceRequest;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceDetailResponse;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceListResponse;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceSummaryResponse;
import com.calio.calendar.groupspace.controller.dto.UpdateGroupSpaceRequest;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.domain.GroupSpaceFields;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupSpaceService {

    private final GroupSpaceRepository groupSpaceRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final AccountRepository accountRepository;
    private final GroupInvitationCommandService invitationCommandService;
    private final GroupScheduleShareCleanupPort groupScheduleShareCleanupPort;
    private final Clock clock;

    @Autowired
    public GroupSpaceService(
            GroupSpaceRepository groupSpaceRepository,
            GroupMemberRepository groupMemberRepository,
            AccountRepository accountRepository,
            GroupInvitationCommandService invitationCommandService,
            GroupScheduleShareCleanupPort groupScheduleShareCleanupPort,
            Clock clock
    ) {
        this.groupSpaceRepository = groupSpaceRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.accountRepository = accountRepository;
        this.invitationCommandService = invitationCommandService;
        this.groupScheduleShareCleanupPort = groupScheduleShareCleanupPort;
        this.clock = clock;
    }

    @Transactional
    public GroupSpaceDetailResponse create(Long accountId, CreateGroupSpaceRequest request) {
        ensureAccountExists(accountId);
        String name = GroupSpaceFields.normalizeName(request.name());
        String nickname = GroupSpaceFields.normalizeNickname(request.nickname());
        String emoji = GroupSpaceFields.canonicalizeEmoji(request.emoji());
        Instant now = clock.instant();

        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace(accountId, name, emoji)
        );
        GroupMember membership = saveOwnerMembership(groupSpace, accountId, nickname, now);
        return GroupSpaceDetailResponse.from(groupSpace, membership, 1);
    }

    @Transactional(readOnly = true)
    public GroupSpaceListResponse list(Long accountId) {
        List<GroupSpaceSummaryResponse> groupSpaces = groupMemberRepository
                .findByAccountIdAndStatusOrderByStatusChangedAtDesc(accountId, GroupMemberStatus.ACTIVE)
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
        GroupSpace groupSpace = findLockedGroupSpace(groupSpaceId);
        GroupMember membership = findActiveMembership(groupSpaceId, accountId);
        requireOwner(groupSpace, membership);

        String name = GroupSpaceFields.normalizeName(request.name());
        String emoji = GroupSpaceFields.canonicalizeEmoji(request.emoji());
        groupSpace.update(name, emoji);
        groupSpaceRepository.flush();
        return GroupSpaceDetailResponse.from(groupSpace, membership, activeMemberCount(groupSpaceId));
    }

    @Transactional
    public void delete(Long accountId, Long groupSpaceId) {
        GroupSpace groupSpace = findLockedGroupSpace(groupSpaceId);
        GroupMember membership = findActiveMembership(groupSpaceId, accountId);
        requireOwner(groupSpace, membership);

        groupMemberRepository.findAllByGroupSpaceIdForUpdateOrderById(groupSpaceId);
        groupScheduleShareCleanupPort.cleanupGroupShares(groupSpaceId);
        invitationCommandService.deleteAllByGroupSpaceId(groupSpaceId);
        groupMemberRepository.deleteAllByGroupSpaceId(groupSpaceId);
        groupSpaceRepository.delete(groupSpace);
    }

    private GroupMember saveOwnerMembership(
            GroupSpace groupSpace,
            Long accountId,
            String nickname,
            Instant now
    ) {
        return groupMemberRepository.saveAndFlush(
                new GroupMember(groupSpace, accountId, nickname, now)
        );
    }

    private void ensureAccountExists(Long accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new CalioException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private GroupSpace findLockedGroupSpace(Long groupSpaceId) {
        return groupSpaceRepository.findByIdForUpdate(groupSpaceId)
                .orElseThrow(GroupSpaceService::groupSpaceNotFound);
    }

    private GroupMember findActiveMembership(Long groupSpaceId, Long accountId) {
        return groupMemberRepository.findByGroupSpaceIdAndAccountIdAndStatus(
                        groupSpaceId,
                        accountId,
                        GroupMemberStatus.ACTIVE
                )
                .orElseThrow(GroupSpaceService::groupSpaceNotFound);
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
        return groupMemberRepository.countByGroupSpace_IdAndStatus(
                groupSpaceId,
                GroupMemberStatus.ACTIVE
        );
    }

    private static CalioException groupSpaceNotFound() {
        return new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND);
    }
}
