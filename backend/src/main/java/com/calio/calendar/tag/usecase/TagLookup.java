package com.calio.calendar.tag.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import org.springframework.stereotype.Component;

@Component
public class TagLookup {

  private static final String FALLBACK_TAG_TITLE = "기타";

  private final TagRepository tagRepository;

  public TagLookup(TagRepository tagRepository) {
    this.tagRepository = tagRepository;
  }

  public Tag getPersonalTagOrDefault(Long accountId, Long tagId) {
    return tagId == null ? getPersonalFallbackTag() : getPersonalTag(accountId, tagId);
  }

  public Tag getPersonalTag(Long accountId, Long tagId) {
    return tagRepository
        .findByIdAndTagTypeAndAccountIsNullAndGroupSpaceIsNull(tagId, TagType.PERSONAL_DEFAULT)
        .or(() -> tagRepository.findByIdAndTagTypeAndAccount_Id(tagId, TagType.CUSTOM, accountId))
        .orElseThrow(() -> new CalioException(ErrorCode.TAG_NOT_FOUND));
  }

  public Tag getPersonalCustomTag(Long accountId, Long tagId) {
    return tagRepository
        .findByIdAndTagTypeAndAccount_Id(tagId, TagType.CUSTOM, accountId)
        .orElseThrow(() -> new CalioException(ErrorCode.TAG_NOT_FOUND));
  }

  public Tag getPersonalFallbackTag() {
    return tagRepository
        .findFirstByTagTypeAndTitleAndAccountIsNullAndGroupSpaceIsNullOrderByIdAsc(
            TagType.PERSONAL_DEFAULT, FALLBACK_TAG_TITLE)
        .orElseThrow(() -> new CalioException(ErrorCode.DEFAULT_TAG_NOT_FOUND));
  }

  public Tag getGroupCustomTag(Long groupSpaceId, Long tagId) {
    return tagRepository
        .findByIdAndGroupSpace_Id(tagId, groupSpaceId)
        .filter(tag -> tag.getTagType() == TagType.CUSTOM)
        .orElseThrow(() -> new CalioException(ErrorCode.GROUP_TAG_NOT_FOUND));
  }

  public Tag getGroupDefaultTag(Long groupSpaceId) {
    return tagRepository
        .findByTagTypeAndGroupSpace_Id(TagType.GROUP_DEFAULT, groupSpaceId)
        .orElseThrow(() -> new CalioException(ErrorCode.GROUP_DEFAULT_TAG_NOT_FOUND));
  }

  public Tag getGroupTagOrDefault(Long groupSpaceId, Long tagId) {
    if (tagId == null) {
      return getGroupDefaultTag(groupSpaceId);
    }
    return tagRepository
        .findByIdAndGroupSpace_Id(tagId, groupSpaceId)
        .orElseThrow(() -> new CalioException(ErrorCode.GROUP_TAG_NOT_FOUND));
  }
}
