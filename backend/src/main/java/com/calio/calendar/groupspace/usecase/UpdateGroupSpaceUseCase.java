package com.calio.calendar.groupspace.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceDetailResponse;
import com.calio.calendar.groupspace.controller.dto.UpdateGroupSpaceRequest;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.domain.GroupSpaceEmoji;
import com.calio.calendar.groupspace.domain.GroupSpaceName;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateGroupSpaceUseCase {

  private final GroupSpaceRepository groupSpaceRepository;
  private final GroupMemberRepository groupMemberRepository;

  public UpdateGroupSpaceUseCase(
      GroupSpaceRepository groupSpaceRepository, GroupMemberRepository groupMemberRepository) {
    this.groupSpaceRepository = groupSpaceRepository;
    this.groupMemberRepository = groupMemberRepository;
  }

  @Transactional
  public GroupSpaceDetailResponse update(
      Long accountId, Long groupSpaceId, UpdateGroupSpaceRequest request) {
    GroupSpace groupSpace = lockGroupSpace(groupSpaceId);
    GroupMember membership = getActiveMembership(groupSpaceId, accountId);
    requireOwner(groupSpace, membership);
    groupSpace.update(
        new GroupSpaceName(request.name()), GroupSpaceEmoji.fromNullable(request.emoji()));
    int memberCount =
        groupMemberRepository.countByGroupSpaceIdAndStatus(groupSpaceId, GroupMemberStatus.ACTIVE);
    return GroupSpaceDetailResponse.from(groupSpace, membership, memberCount);
  }

  private GroupSpace lockGroupSpace(Long groupSpaceId) {
    return groupSpaceRepository
        .findByIdForUpdate(groupSpaceId)
        .orElseThrow(UpdateGroupSpaceUseCase::groupSpaceNotFound);
  }

  private GroupMember getActiveMembership(Long groupSpaceId, Long accountId) {
    return groupMemberRepository
        .findByGroupSpaceIdAndAccountIdAndStatus(groupSpaceId, accountId, GroupMemberStatus.ACTIVE)
        .orElseThrow(UpdateGroupSpaceUseCase::groupSpaceNotFound);
  }

  private void requireOwner(GroupSpace groupSpace, GroupMember member) {
    if (!member.roleFor(groupSpace.getOwnerAccountId()).isOwner()) {
      throw new CalioException(ErrorCode.GROUP_OWNER_REQUIRED);
    }
  }

  private static CalioException groupSpaceNotFound() {
    return new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND);
  }
}
