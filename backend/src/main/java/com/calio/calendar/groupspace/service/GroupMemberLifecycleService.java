package com.calio.calendar.groupspace.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import java.util.List;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupMemberLifecycleService {

    private final GroupSpaceRepository groupSpaceRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final List<GroupMemberDeactivationCleanup> deactivationCleanups;
    private final Clock clock;

    public GroupMemberLifecycleService(
            GroupSpaceRepository groupSpaceRepository,
            GroupMemberRepository groupMemberRepository,
            List<GroupMemberDeactivationCleanup> deactivationCleanups,
            Clock clock
    ) {
        this.groupSpaceRepository = groupSpaceRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.deactivationCleanups = List.copyOf(deactivationCleanups);
        this.clock = clock;
    }

    @Transactional
    public void deactivate(
            Long groupSpaceId,
            Long memberId,
            GroupMemberStatus inactiveStatus
    ) {
        groupSpaceRepository.findByIdForUpdate(groupSpaceId)
                .orElseThrow(GroupMemberLifecycleService::groupSpaceNotFound);
        GroupMember member = groupMemberRepository.findByGroupSpaceIdAndIdForUpdate(
                        groupSpaceId,
                        memberId
                )
                .filter(candidate -> candidate.getStatus() == GroupMemberStatus.ACTIVE)
                .orElseThrow(GroupMemberLifecycleService::groupSpaceNotFound);

        deactivationCleanups.forEach(cleanup -> cleanup.deleteByMemberId(member.getId()));
        member.deactivate(inactiveStatus, clock.instant());
    }

    private static CalioException groupSpaceNotFound() {
        return new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND);
    }
}
