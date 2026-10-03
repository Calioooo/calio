package com.calio.calendar.groupspace.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceListResponse;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceSummaryResponse;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListMyGroupSpacesUseCase {

  private final GroupMemberRepository groupMemberRepository;
  private final GroupSpaceRepository groupSpaceRepository;

  public ListMyGroupSpacesUseCase(
      GroupMemberRepository groupMemberRepository, GroupSpaceRepository groupSpaceRepository) {
    this.groupMemberRepository = groupMemberRepository;
    this.groupSpaceRepository = groupSpaceRepository;
  }

  @Transactional(readOnly = true)
  public GroupSpaceListResponse list(Long accountId) {
    List<GroupMember> memberships =
        groupMemberRepository.findByAccountIdAndStatusOrderByStatusChangedAtDescGroupSpaceIdDesc(
            accountId, GroupMemberStatus.ACTIVE);
    Map<Long, GroupSpace> groupSpacesById =
        groupSpaceRepository
            .findAllById(memberships.stream().map(GroupMember::getGroupSpaceId).toList())
            .stream()
            .collect(java.util.stream.Collectors.toMap(GroupSpace::getId, Function.identity()));
    Map<Long, Long> activeMemberCounts =
        groupMemberRepository
            .findAllByGroupSpaceIdInAndStatus(
                groupSpacesById.keySet().stream().toList(), GroupMemberStatus.ACTIVE)
            .stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    GroupMember::getGroupSpaceId, java.util.stream.Collectors.counting()));
    List<GroupSpaceSummaryResponse> groupSpaces =
        memberships.stream()
            .map(
                membership ->
                    GroupSpaceSummaryResponse.from(
                        requireGroupSpace(groupSpacesById, membership.getGroupSpaceId()),
                        membership,
                        Math.toIntExact(
                            activeMemberCounts.getOrDefault(membership.getGroupSpaceId(), 0L))))
            .toList();
    return new GroupSpaceListResponse(groupSpaces);
  }

  private GroupSpace requireGroupSpace(Map<Long, GroupSpace> groupSpacesById, Long groupSpaceId) {
    GroupSpace groupSpace = groupSpacesById.get(groupSpaceId);
    if (groupSpace == null) {
      throw new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND);
    }
    return groupSpace;
  }
}
