package com.calio.calendar.groupspace.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceDetailResponse;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetGroupSpaceUseCase {

  private final GroupSpaceRepository groupSpaceRepository;
  private final GroupMemberRepository groupMemberRepository;

  public GetGroupSpaceUseCase(
      GroupSpaceRepository groupSpaceRepository, GroupMemberRepository groupMemberRepository) {
    this.groupSpaceRepository = groupSpaceRepository;
    this.groupMemberRepository = groupMemberRepository;
  }

  @Transactional(readOnly = true)
  public GroupSpaceDetailResponse get(Long accountId, Long groupSpaceId) {
    GroupMember membership = getActiveMembership(groupSpaceId, accountId);
    GroupSpace groupSpace = getGroupSpace(groupSpaceId);
    int memberCount =
        groupMemberRepository.countByGroupSpaceIdAndStatus(groupSpaceId, GroupMemberStatus.ACTIVE);
    return GroupSpaceDetailResponse.from(groupSpace, membership, memberCount);
  }

  private GroupMember getActiveMembership(Long groupSpaceId, Long accountId) {
    return groupMemberRepository
        .findByGroupSpaceIdAndAccountIdAndStatus(groupSpaceId, accountId, GroupMemberStatus.ACTIVE)
        .orElseThrow(() -> new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND));
  }

  private GroupSpace getGroupSpace(Long groupSpaceId) {
    return groupSpaceRepository
        .findById(groupSpaceId)
        .orElseThrow(() -> new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND));
  }
}
