package com.calio.calendar.groupspace.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.controller.dto.TransferGroupOwnerResponse;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferGroupOwnershipUseCase {

  private final GroupSpaceRepository groupSpaceRepository;
  private final GroupMemberRepository groupMemberRepository;

  public TransferGroupOwnershipUseCase(
      GroupSpaceRepository groupSpaceRepository, GroupMemberRepository groupMemberRepository) {
    this.groupSpaceRepository = groupSpaceRepository;
    this.groupMemberRepository = groupMemberRepository;
  }

  @Transactional
  public TransferGroupOwnerResponse transfer(
      Long accountId, Long groupSpaceId, Long targetMemberId) {
    GroupSpace groupSpace = lockGroupSpace(groupSpaceId);
    List<GroupMember> members =
        groupMemberRepository.findAllByGroupSpaceIdForUpdateOrderById(groupSpaceId);
    GroupMember actor = requireActiveMember(members, accountId);
    requireOwner(groupSpace, actor);
    GroupMember target =
        members.stream()
            .filter(member -> member.getId().equals(targetMemberId))
            .filter(member -> member.getStatus() == GroupMemberStatus.ACTIVE)
            .findFirst()
            .orElseThrow(() -> new CalioException(ErrorCode.GROUP_MEMBER_NOT_FOUND));
    if (target.getId().equals(actor.getId())) {
      throw new CalioException(ErrorCode.GROUP_OWNER_TRANSFER_INVALID);
    }
    groupSpace.transferOwnershipTo(target.getAccountId());
    return TransferGroupOwnerResponse.from(groupSpace, actor, target);
  }

  private GroupSpace lockGroupSpace(Long groupSpaceId) {
    return groupSpaceRepository
        .findByIdForUpdate(groupSpaceId)
        .orElseThrow(() -> new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND));
  }

  private GroupMember requireActiveMember(List<GroupMember> members, Long accountId) {
    return members.stream()
        .filter(member -> member.getAccountId().equals(accountId))
        .filter(member -> member.getStatus() == GroupMemberStatus.ACTIVE)
        .findFirst()
        .orElseThrow(() -> new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND));
  }

  private void requireOwner(GroupSpace groupSpace, GroupMember member) {
    if (!member.roleFor(groupSpace.getOwnerAccountId()).isOwner()) {
      throw new CalioException(ErrorCode.GROUP_OWNER_REQUIRED);
    }
  }
}
