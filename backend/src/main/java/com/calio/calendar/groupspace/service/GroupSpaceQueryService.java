package com.calio.calendar.groupspace.service;

import com.calio.calendar.account.repository.AccountRepository;
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
public class GroupSpaceQueryService {

    private final GroupSpaceRepository groupSpaceRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final AccountRepository accountRepository;

    public GroupSpaceQueryService(
            GroupSpaceRepository groupSpaceRepository,
            GroupMemberRepository groupMemberRepository,
            AccountRepository accountRepository
    ) {
        this.groupSpaceRepository = groupSpaceRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.accountRepository = accountRepository;
    }

    public boolean hasAccount(Long accountId) {
        return accountRepository.existsById(accountId);
    }

    public List<GroupMember> listActiveMemberships(Long accountId) {
        return groupMemberRepository
                .findByAccountIdAndStatusOrderByStatusChangedAtDescGroupSpaceIdDesc(
                        accountId,
                        GroupMemberStatus.ACTIVE
                );
    }

    public GroupMember getActiveMembership(Long groupSpaceId, Long accountId) {
        return groupMemberRepository.findByGroupSpaceIdAndAccountIdAndStatus(
                        groupSpaceId,
                        accountId,
                        GroupMemberStatus.ACTIVE
                )
                .orElseThrow(GroupSpaceQueryService::groupSpaceNotFound);
    }

    public int getActiveMemberCount(Long groupSpaceId) {
        return groupMemberRepository.countByGroupSpace_IdAndStatus(
                groupSpaceId,
                GroupMemberStatus.ACTIVE
        );
    }

    private static CalioException groupSpaceNotFound() {
        return new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND);
    }
}
