package com.calio.calendar.groupspace.service;

import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.controller.dto.CreateGroupSpaceRequest;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceListResponse;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceResponseDto;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceSummaryResponse;
import com.calio.calendar.groupspace.controller.dto.UpdateGroupSpaceRequest;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupSpaceService {

    private static final String ACTIVE_NICKNAME_CONSTRAINT = "uk_group_member_active_nickname";

    private final GroupSpaceRepository groupSpaceRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final AccountRepository accountRepository;
    private final GroupSpaceInputNormalizer inputNormalizer;
    private final List<GroupSpaceDeletionCleanup> deletionCleanups;

    public GroupSpaceService(
            GroupSpaceRepository groupSpaceRepository,
            GroupMemberRepository groupMemberRepository,
            AccountRepository accountRepository,
            GroupSpaceInputNormalizer inputNormalizer,
            List<GroupSpaceDeletionCleanup> deletionCleanups
    ) {
        this.groupSpaceRepository = groupSpaceRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.accountRepository = accountRepository;
        this.inputNormalizer = inputNormalizer;
        this.deletionCleanups = deletionCleanups;
    }

    @Transactional
    public GroupSpaceResponseDto create(Long accountId, CreateGroupSpaceRequest request) {
        requireExistingAccount(accountId);
        String name = inputNormalizer.normalizeName(request.name());
        String emoji = inputNormalizer.normalizeEmoji(request.emoji());
        String nickname = inputNormalizer.normalizeNickname(request.nickname());

        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(new GroupSpace(name, emoji, accountId));
        GroupMember ownerMembership = saveOwnerMembership(groupSpace.getId(), accountId, nickname);
        return response(groupSpace, ownerMembership);
    }

    @Transactional(readOnly = true)
    public GroupSpaceListResponse list(Long accountId) {
        List<GroupSpaceSummaryResponse> groupSpaces = groupMemberRepository
                .findMembershipsForList(accountId, GroupMemberStatus.ACTIVE)
                .stream()
                .map(this::summary)
                .toList();
        return new GroupSpaceListResponse(groupSpaces);
    }

    @Transactional(readOnly = true)
    public GroupSpaceResponseDto get(Long accountId, Long groupSpaceId) {
        GroupMember membership = findActiveMembership(groupSpaceId, accountId);
        GroupSpace groupSpace = findGroupSpace(groupSpaceId);
        return response(groupSpace, membership);
    }

    @Transactional
    public GroupSpaceResponseDto patch(
            Long accountId,
            Long groupSpaceId,
            UpdateGroupSpaceRequest request
    ) {
        if (!request.hasAnyField()) {
            throw new CalioException(ErrorCode.VALIDATION_FAILED);
        }

        LockedOwner lockedOwner = lockAndRequireOwner(groupSpaceId, accountId);
        applyPatch(lockedOwner.groupSpace(), request);
        groupSpaceRepository.flush();
        return response(lockedOwner.groupSpace(), lockedOwner.membership());
    }

    @Transactional
    public void delete(Long accountId, Long groupSpaceId) {
        LockedOwner lockedOwner = lockAndRequireOwner(groupSpaceId, accountId);
        deletionCleanups.forEach(cleanup -> cleanup.deleteGroupSchedules(groupSpaceId));
        deletionCleanups.forEach(cleanup -> cleanup.deleteGroupInvitations(groupSpaceId));
        groupMemberRepository.deleteAllInBatch(lockedOwner.lockedMembers());
        groupMemberRepository.flush();
        groupSpaceRepository.delete(lockedOwner.groupSpace());
        groupSpaceRepository.flush();
    }

    private GroupMember saveOwnerMembership(Long groupSpaceId, Long accountId, String nickname) {
        try {
            return groupMemberRepository.saveAndFlush(new GroupMember(groupSpaceId, accountId, nickname));
        } catch (DataIntegrityViolationException exception) {
            if (isActiveNicknameConflict(exception)) {
                throw new CalioException(ErrorCode.GROUP_MEMBER_NICKNAME_CONFLICT, exception);
            }
            throw exception;
        }
    }

    private LockedOwner lockAndRequireOwner(Long groupSpaceId, Long accountId) {
        GroupSpace groupSpace = groupSpaceRepository.findByIdForUpdate(groupSpaceId)
                .orElseThrow(GroupSpaceService::groupSpaceNotFound);
        List<GroupMember> members = groupMemberRepository.findAllByGroupSpaceIdForUpdate(groupSpaceId);
        GroupMember membership = members.stream()
                .filter(member -> member.getAccountId().equals(accountId) && member.isActive())
                .findFirst()
                .orElseThrow(GroupSpaceService::groupSpaceNotFound);
        if (!groupSpace.isOwner(accountId)) {
            throw new CalioException(ErrorCode.GROUP_OWNER_REQUIRED);
        }
        return new LockedOwner(groupSpace, membership, members);
    }

    private void applyPatch(GroupSpace groupSpace, UpdateGroupSpaceRequest request) {
        if (request.isNamePresent()) {
            groupSpace.changeName(inputNormalizer.normalizeName(request.name()));
        }
        if (request.isEmojiPresent()) {
            groupSpace.changeEmoji(inputNormalizer.normalizeEmoji(request.emoji()));
        }
    }

    private GroupSpaceSummaryResponse summary(GroupMember member) {
        GroupSpace groupSpace = findGroupSpace(member.getGroupSpaceId());
        return GroupSpaceSummaryResponse.from(groupSpace, member, countActiveMembers(groupSpace.getId()));
    }

    private GroupSpaceResponseDto response(GroupSpace groupSpace, GroupMember member) {
        return GroupSpaceResponseDto.from(groupSpace, member, countActiveMembers(groupSpace.getId()));
    }

    private int countActiveMembers(Long groupSpaceId) {
        return groupMemberRepository.countByGroupSpaceIdAndStatus(groupSpaceId, GroupMemberStatus.ACTIVE);
    }

    private GroupMember findActiveMembership(Long groupSpaceId, Long accountId) {
        return groupMemberRepository.findByGroupSpaceIdAndAccountIdAndStatus(
                        groupSpaceId,
                        accountId,
                        GroupMemberStatus.ACTIVE
                )
                .orElseThrow(GroupSpaceService::groupSpaceNotFound);
    }

    private GroupSpace findGroupSpace(Long groupSpaceId) {
        return groupSpaceRepository.findById(groupSpaceId)
                .orElseThrow(GroupSpaceService::groupSpaceNotFound);
    }

    private void requireExistingAccount(Long accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new CalioException(ErrorCode.AUTH_TOKEN_INVALID);
        }
    }

    private boolean isActiveNicknameConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && message.contains(ACTIVE_NICKNAME_CONSTRAINT)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static CalioException groupSpaceNotFound() {
        return new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND);
    }

    private record LockedOwner(
            GroupSpace groupSpace,
            GroupMember membership,
            List<GroupMember> lockedMembers
    ) {
    }
}
