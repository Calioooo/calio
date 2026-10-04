package com.calio.calendar.groupspace.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteGroupSpaceUseCase {

  private final GroupSpaceRepository groupSpaceRepository;
  private final GroupMemberRepository groupMemberRepository;
  private final GroupSpaceDeletionCleanup deletionCleanup;

  public DeleteGroupSpaceUseCase(
      GroupSpaceRepository groupSpaceRepository,
      GroupMemberRepository groupMemberRepository,
      GroupSpaceDeletionCleanup deletionCleanup) {
    this.groupSpaceRepository = groupSpaceRepository;
    this.groupMemberRepository = groupMemberRepository;
    this.deletionCleanup = deletionCleanup;
  }

  @Transactional
  public void delete(Long accountId, Long groupSpaceId) {
    GroupSpace groupSpace = lockGroupSpace(groupSpaceId);
    GroupMember membership = getActiveMembership(groupSpaceId, accountId);
    requireOwner(groupSpace, membership);
    groupMemberRepository.findAllByGroupSpaceIdForUpdateOrderById(groupSpaceId);
    deletionCleanup.delete(groupSpace);
  }

  private GroupSpace lockGroupSpace(Long groupSpaceId) {
    return groupSpaceRepository
        .findByIdForUpdate(groupSpaceId)
        .orElseThrow(() -> new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND));
  }

  private GroupMember getActiveMembership(Long groupSpaceId, Long accountId) {
    return groupMemberRepository
        .findByGroupSpaceIdAndAccountIdAndStatus(groupSpaceId, accountId, GroupMemberStatus.ACTIVE)
        .orElseThrow(() -> new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND));
  }

  private void requireOwner(GroupSpace groupSpace, GroupMember member) {
    if (!member.roleFor(groupSpace.getOwnerAccountId()).isOwner()) {
      throw new CalioException(ErrorCode.GROUP_OWNER_REQUIRED);
    }
  }
}
