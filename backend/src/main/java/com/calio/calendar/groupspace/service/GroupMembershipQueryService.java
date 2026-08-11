package com.calio.calendar.groupspace.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GroupMembershipQueryService {

    private final GroupSpaceRepository groupSpaceRepository;
    private final GroupMemberRepository groupMemberRepository;

    public GroupMembershipQueryService(
            GroupSpaceRepository groupSpaceRepository,
            GroupMemberRepository groupMemberRepository
    ) {
        this.groupSpaceRepository = groupSpaceRepository;
        this.groupMemberRepository = groupMemberRepository;
    }

    public GroupSpace getGroupSpace(Long groupSpaceId) {
        return groupSpaceRepository.findById(groupSpaceId)
                .orElseThrow(GroupMembershipQueryService::groupSpaceNotFound);
    }

    public GroupSpace lockGroupSpace(Long groupSpaceId) {
        return groupSpaceRepository.findByIdForUpdate(groupSpaceId)
                .orElseThrow(GroupMembershipQueryService::groupSpaceNotFound);
    }

    public GroupSpace lockGroupSpaceForInvitation(Long groupSpaceId) {
        return groupSpaceRepository.findByIdForUpdate(groupSpaceId)
                .orElseThrow(GroupMembershipQueryService::invitationNotFound);
    }

    public List<GroupMember> lockMembers(Long groupSpaceId) {
        return groupMemberRepository.findAllByGroupSpaceIdForUpdateOrderById(groupSpaceId);
    }

    public GroupMember getActiveMembership(Long groupSpaceId, Long accountId) {
        return groupMemberRepository.findByGroupSpaceIdAndAccountIdAndStatus(
                        groupSpaceId,
                        accountId,
                        GroupMemberStatus.ACTIVE
                )
                .orElseThrow(GroupMembershipQueryService::groupSpaceNotFound);
    }

    public List<GroupMember> listActiveMembers(Long groupSpaceId) {
        return groupMemberRepository.findAllByGroupSpaceIdAndStatus(
                groupSpaceId,
                GroupMemberStatus.ACTIVE
        );
    }

    public boolean hasActiveNicknameConflict(
            Long groupSpaceId,
            String nickname,
            Long excludedMemberId
    ) {
        return groupMemberRepository.hasActiveNicknameConflict(
                groupSpaceId,
                nickname,
                excludedMemberId
        );
    }

    private static CalioException groupSpaceNotFound() {
        return new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND);
    }

    private static CalioException invitationNotFound() {
        return new CalioException(ErrorCode.GROUP_INVITATION_NOT_FOUND);
    }
}
