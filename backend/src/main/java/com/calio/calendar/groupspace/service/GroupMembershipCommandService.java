package com.calio.calendar.groupspace.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GroupMembershipCommandService {

    private final GroupMemberRepository groupMemberRepository;
    private final GroupSpaceRepository groupSpaceRepository;

    public GroupMembershipCommandService(
            GroupMemberRepository groupMemberRepository,
            GroupSpaceRepository groupSpaceRepository
    ) {
        this.groupMemberRepository = groupMemberRepository;
        this.groupSpaceRepository = groupSpaceRepository;
    }

    public GroupMember create(
            GroupSpace groupSpace,
            Long accountId,
            String nickname,
            Instant now
    ) {
        return groupMemberRepository.saveAndFlush(
                new GroupMember(groupSpace, accountId, nickname, now)
        );
    }

    public GroupSpace lockGroupSpace(Long groupSpaceId) {
        return groupSpaceRepository.findByIdForUpdate(groupSpaceId)
                .orElseThrow(GroupMembershipCommandService::groupSpaceNotFound);
    }

    public GroupSpace lockGroupSpaceForInvitation(Long groupSpaceId) {
        return groupSpaceRepository.findByIdForUpdate(groupSpaceId)
                .orElseThrow(GroupMembershipCommandService::invitationNotFound);
    }

    public List<GroupMember> lockMembers(Long groupSpaceId) {
        return groupMemberRepository.findAllByGroupSpaceIdForUpdateOrderById(groupSpaceId);
    }

    public GroupMember lockActiveMember(Long groupSpaceId, Long accountId) {
        GroupMember member = groupMemberRepository.findByGroupSpaceIdAndAccountIdForUpdate(
                        groupSpaceId,
                        accountId
                )
                .orElseThrow(GroupMembershipCommandService::groupSpaceNotFound);
        if (member.getStatus() != GroupMemberStatus.ACTIVE) {
            throw groupSpaceNotFound();
        }
        return member;
    }

    public void changeToActive(GroupMember member, String nickname, Instant now) {
        member.reactivate(nickname, now);
        groupMemberRepository.flush();
    }

    public void changeStatus(
            GroupMember member,
            GroupMemberStatus status,
            Instant now
    ) {
        member.deactivate(status, now);
        groupMemberRepository.flush();
    }

    public void changeOwnership(GroupSpace groupSpace, Long ownerAccountId) {
        groupSpace.transferOwnershipTo(ownerAccountId);
        groupSpaceRepository.flush();
    }

    private static CalioException groupSpaceNotFound() {
        return new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND);
    }

    private static CalioException invitationNotFound() {
        return new CalioException(ErrorCode.GROUP_INVITATION_NOT_FOUND);
    }
}
