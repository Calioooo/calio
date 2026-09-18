package com.calio.calendar.tag.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.tag.controller.dto.TagResponse;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateGroupCustomTagUseCase {

  private final GroupMembershipQueryService groupMembershipQueryService;
  private final TagRepository tagRepository;

  public UpdateGroupCustomTagUseCase(
      GroupMembershipQueryService groupMembershipQueryService, TagRepository tagRepository) {
    this.groupMembershipQueryService = groupMembershipQueryService;
    this.tagRepository = tagRepository;
  }

  @Transactional
  public TagResponse update(
      Long accountId, Long groupSpaceId, Long tagId, String title, String colorCode) {
    groupMembershipQueryService.getActiveMembership(groupSpaceId, accountId);
    Tag tag =
        tagRepository
            .findByIdAndGroupSpace_Id(tagId, groupSpaceId)
            .filter(candidate -> candidate.getTagType() == TagType.CUSTOM)
            .orElseThrow(() -> new CalioException(ErrorCode.GROUP_TAG_NOT_FOUND));
    if (tagRepository.existsByTagTypeAndTitleAndGroupSpace_IdAndIdNot(
        TagType.CUSTOM, title, groupSpaceId, tagId)) {
      throw new CalioException(ErrorCode.VALIDATION_FAILED);
    }
    tag.update(title, colorCode);
    return TagResponse.from(tag);
  }
}
