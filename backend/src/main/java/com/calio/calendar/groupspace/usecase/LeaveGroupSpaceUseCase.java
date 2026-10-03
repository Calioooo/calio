package com.calio.calendar.groupspace.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaveGroupSpaceUseCase {

  private final GroupSpaceRepository groupSpaceRepository;
  private final GroupMemberRepository groupMemberRepository;
  private final GroupMemberDepartureCleanup departureCleanup;
  private final GroupSpaceDeletionCleanup deletionCleanup;
  private final Clock clock;

  public LeaveGroupSpaceUseCase(
      GroupSpaceRepository groupSpaceRepository,
      GroupMemberRepository groupMemberRepository,
      GroupMemberDepartureCleanup departureCleanup,
      GroupSpaceDeletionCleanup deletionCleanup,
      Clock clock) {
    this.groupSpaceRepository = groupSpaceRepository;
    this.groupMemberRepository = groupMemberRepository;
    this.departureCleanup = departureCleanup;
    this.deletionCleanup = deletionCleanup;
    this.clock = clock;
  }

  @Transactional
  public void leave(Long accountId, Long groupSpaceId) {
    GroupSpace groupSpace = lockGroupSpace(groupSpaceId);
    List<GroupMember> members =
        groupMemberRepository.findAllByGroupSpaceIdForUpdateOrderById(groupSpaceId);
    GroupMember actor = requireActiveMember(members, accountId);
    if (actor.roleFor(groupSpace.getOwnerAccountId()).isOwner() && activeMemberCount(members) > 1) {
      throw new CalioException(ErrorCode.GROUP_OWNER_TRANSFER_REQUIRED);
    }
    if (actor.roleFor(groupSpace.getOwnerAccountId()).isOwner()) {
      deletionCleanup.delete(groupSpace);
      return;
    }
    departureCleanup.clean(actor);
    actor.deactivate(GroupMemberStatus.LEFT, clock.instant());
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

  private int activeMemberCount(List<GroupMember> members) {
    return (int)
        members.stream().filter(member -> member.getStatus() == GroupMemberStatus.ACTIVE).count();
  }
}
