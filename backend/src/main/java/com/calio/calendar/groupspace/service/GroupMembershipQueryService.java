package com.calio.calendar.groupspace.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GroupMembershipQueryService {

    private final GroupMemberRepository groupMemberRepository;

    public GroupMembershipQueryService(
            GroupMemberRepository groupMemberRepository
    ) {
        this.groupMemberRepository = groupMemberRepository;
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

    public List<GroupMember> listActiveMemberships(Long accountId, List<Long> groupSpaceIds) {
        return groupMemberRepository.findAllActiveByAccountIdAndGroupSpaceIds(accountId, groupSpaceIds);
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

}
