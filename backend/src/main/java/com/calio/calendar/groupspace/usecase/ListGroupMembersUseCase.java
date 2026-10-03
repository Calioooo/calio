package com.calio.calendar.groupspace.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.controller.dto.GroupMemberListResponse;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListGroupMembersUseCase {

  private final GroupSpaceRepository groupSpaceRepository;
  private final GroupMemberRepository groupMemberRepository;

  public ListGroupMembersUseCase(
      GroupSpaceRepository groupSpaceRepository, GroupMemberRepository groupMemberRepository) {
    this.groupSpaceRepository = groupSpaceRepository;
    this.groupMemberRepository = groupMemberRepository;
  }

  @Transactional(readOnly = true)
  public GroupMemberListResponse list(Long accountId, Long groupSpaceId) {
    GroupSpace groupSpace = getGroupSpace(groupSpaceId);
    getActiveMembership(groupSpaceId, accountId);
    List<GroupMember> activeMembers =
        groupMemberRepository
            .findAllByGroupSpaceIdAndStatus(groupSpaceId, GroupMemberStatus.ACTIVE)
            .stream()
            .sorted(memberOrder(groupSpace))
            .toList();
    return GroupMemberListResponse.from(activeMembers, groupSpace);
  }

  private GroupSpace getGroupSpace(Long groupSpaceId) {
    return groupSpaceRepository
        .findById(groupSpaceId)
        .orElseThrow(ListGroupMembersUseCase::groupSpaceNotFound);
  }

  private void getActiveMembership(Long groupSpaceId, Long accountId) {
    groupMemberRepository
        .findByGroupSpaceIdAndAccountIdAndStatus(groupSpaceId, accountId, GroupMemberStatus.ACTIVE)
        .orElseThrow(ListGroupMembersUseCase::groupSpaceNotFound);
  }

  private Comparator<GroupMember> memberOrder(GroupSpace groupSpace) {
    return Comparator.comparing(
            (GroupMember member) -> !member.roleFor(groupSpace.getOwnerAccountId()).isOwner())
        .thenComparing(GroupMember::getStatusChangedAt)
        .thenComparing(GroupMember::getId);
  }

  private static CalioException groupSpaceNotFound() {
    return new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND);
  }
}
