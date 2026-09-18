package com.calio.calendar.tag.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.groupspace.service.GroupSpaceQueryService;
import com.calio.calendar.tag.controller.dto.TagResponse;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateGroupCustomTagUseCase {

  private final GroupSpaceQueryService groupSpaceQueryService;
  private final GroupMembershipQueryService groupMembershipQueryService;
  private final TagRepository tagRepository;

  public CreateGroupCustomTagUseCase(
      GroupSpaceQueryService groupSpaceQueryService,
      GroupMembershipQueryService groupMembershipQueryService,
      TagRepository tagRepository) {
    this.groupSpaceQueryService = groupSpaceQueryService;
    this.groupMembershipQueryService = groupMembershipQueryService;
    this.tagRepository = tagRepository;
  }

  @Transactional
  public TagResponse create(Long accountId, Long groupSpaceId, String title, String colorCode) {
    GroupSpace groupSpace = groupSpaceQueryService.getGroupSpace(groupSpaceId);
    groupMembershipQueryService.getActiveMembership(groupSpaceId, accountId);
    rejectDuplicateTitle(groupSpaceId, title, null);
    Tag tag = tagRepository.save(Tag.groupCustom(groupSpace, title, colorCode));
    return TagResponse.from(tag);
  }

  private void rejectDuplicateTitle(Long groupSpaceId, String title, Long excludedTagId) {
    boolean exists =
        excludedTagId == null
            ? tagRepository.existsByTagTypeAndTitleAndGroupSpace_Id(
                TagType.CUSTOM, title, groupSpaceId)
            : tagRepository.existsByTagTypeAndTitleAndGroupSpace_IdAndIdNot(
                TagType.CUSTOM, title, groupSpaceId, excludedTagId);
    if (exists) {
      throw new CalioException(ErrorCode.VALIDATION_FAILED);
    }
  }
}
