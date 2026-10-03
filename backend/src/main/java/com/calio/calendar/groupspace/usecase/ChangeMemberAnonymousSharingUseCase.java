package com.calio.calendar.groupspace.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.controller.dto.GroupMembershipResponse;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangeMemberAnonymousSharingUseCase {

  private final GroupMemberRepository groupMemberRepository;
  private final GroupSpaceRepository groupSpaceRepository;

  public ChangeMemberAnonymousSharingUseCase(
      GroupMemberRepository groupMemberRepository, GroupSpaceRepository groupSpaceRepository) {
    this.groupMemberRepository = groupMemberRepository;
    this.groupSpaceRepository = groupSpaceRepository;
  }

  @Transactional
  public GroupMembershipResponse change(Long accountId, Long groupSpaceId, boolean isAnonymous) {
    GroupMember member = lockActiveMember(groupSpaceId, accountId);
    member.changeAnonymous(isAnonymous);
    GroupSpace groupSpace =
        groupSpaceRepository
            .findById(groupSpaceId)
            .orElseThrow(ChangeMemberAnonymousSharingUseCase::groupSpaceNotFound);
    return GroupMembershipResponse.from(member, groupSpace);
  }

  private GroupMember lockActiveMember(Long groupSpaceId, Long accountId) {
    GroupMember member =
        groupMemberRepository
            .findByGroupSpaceIdAndAccountIdForUpdate(groupSpaceId, accountId)
            .orElseThrow(ChangeMemberAnonymousSharingUseCase::groupSpaceNotFound);
    if (member.getStatus() != GroupMemberStatus.ACTIVE) {
      throw groupSpaceNotFound();
    }
    return member;
  }

  private static CalioException groupSpaceNotFound() {
    return new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND);
  }
}
