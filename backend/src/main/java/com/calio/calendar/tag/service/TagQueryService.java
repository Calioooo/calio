package com.calio.calendar.tag.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TagQueryService {

    private static final String FALLBACK_TAG_TITLE = "기타";

    private final TagRepository tagRepository;

    public TagQueryService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    public List<Tag> listAvailableTags(Long accountId) {
        return Stream.concat(
                        listGlobalDefaultTags().stream(),
                        listAccountCustomTags(accountId).stream()
                )
                .sorted(Comparator.comparing(Tag::getId))
                .toList();
    }

    public Tag getTagOrDefault(Long accountId, Long tagId) {
        return tagId == null ? getFallbackTag() : getTag(accountId, tagId);
    }

    public Tag getTag(Long accountId, Long tagId) {
        return getGlobalDefaultTagIfExists(tagId)
                .or(() -> getAccountCustomTagIfExists(accountId, tagId))
                .orElseThrow(() -> new CalioException(ErrorCode.TAG_NOT_FOUND));
    }

    public Tag getCustomTag(Long accountId, Long tagId) {
        return tagRepository.findByIdAndTagTypeAndAccount_Id(tagId, TagType.CUSTOM, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.TAG_NOT_FOUND));
    }

    public Tag getFallbackTag() {
        return tagRepository.findFirstByTagTypeAndTitleAndAccountIsNullAndGroupSpaceIsNullOrderByIdAsc(
                        TagType.PERSONAL_DEFAULT,
                        FALLBACK_TAG_TITLE
                )
                .orElseThrow(() -> new CalioException(ErrorCode.DEFAULT_TAG_NOT_FOUND));
    }

    public List<Tag> listGroupTags(Long groupSpaceId) {
        return tagRepository.findByGroupSpace_IdOrderByIdAsc(groupSpaceId);
    }

    public Tag getGroupCustomTag(Long groupSpaceId, Long tagId) {
        return tagRepository.findByIdAndGroupSpace_Id(tagId, groupSpaceId)
                .filter(tag -> tag.getTagType() == TagType.CUSTOM)
                .orElseThrow(() -> new CalioException(ErrorCode.GROUP_TAG_NOT_FOUND));
    }

    public Tag getGroupDefaultTag(Long groupSpaceId) {
        return tagRepository.findByTagTypeAndGroupSpace_Id(TagType.GROUP_DEFAULT, groupSpaceId)
                .orElseThrow(() -> new CalioException(ErrorCode.GROUP_DEFAULT_TAG_NOT_FOUND));
    }

    public Tag getGroupTagOrDefault(Long groupSpaceId, Long tagId) {
        if (tagId == null) {
            return getGroupDefaultTag(groupSpaceId);
        }
        return tagRepository.findByIdAndGroupSpace_Id(tagId, groupSpaceId)
                .orElseThrow(() -> new CalioException(ErrorCode.GROUP_TAG_NOT_FOUND));
    }

    public boolean hasGroupCustomTagTitle(Long groupSpaceId, String title, Long excludedTagId) {
        if (excludedTagId == null) {
            return tagRepository.existsByTagTypeAndTitleAndGroupSpace_Id(TagType.CUSTOM, title, groupSpaceId);
        }
        return tagRepository.existsByTagTypeAndTitleAndGroupSpace_IdAndIdNot(
                TagType.CUSTOM,
                title,
                groupSpaceId,
                excludedTagId
        );
    }

    private List<Tag> listGlobalDefaultTags() {
        return tagRepository.findByTagTypeAndAccountIsNullAndGroupSpaceIsNullOrderByIdAsc(TagType.PERSONAL_DEFAULT);
    }

    private List<Tag> listAccountCustomTags(Long accountId) {
        return tagRepository.findByTagTypeAndAccount_IdOrderByIdAsc(TagType.CUSTOM, accountId);
    }

    private Optional<Tag> getGlobalDefaultTagIfExists(Long tagId) {
        return tagRepository.findByIdAndTagTypeAndAccountIsNullAndGroupSpaceIsNull(tagId, TagType.PERSONAL_DEFAULT);
    }

    private Optional<Tag> getAccountCustomTagIfExists(Long accountId, Long tagId) {
        return tagRepository.findByIdAndTagTypeAndAccount_Id(tagId, TagType.CUSTOM, accountId);
    }
}
