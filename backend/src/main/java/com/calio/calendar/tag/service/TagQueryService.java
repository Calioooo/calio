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
        return tagRepository.findFirstByTagTypeAndTitleAndAccountIsNullOrderByIdAsc(
                        TagType.DEFAULT,
                        FALLBACK_TAG_TITLE
                )
                .orElseThrow(() -> new CalioException(ErrorCode.DEFAULT_TAG_NOT_FOUND));
    }

    private List<Tag> listGlobalDefaultTags() {
        return tagRepository.findByTagTypeAndAccountIsNullOrderByIdAsc(TagType.DEFAULT);
    }

    private List<Tag> listAccountCustomTags(Long accountId) {
        return tagRepository.findByTagTypeAndAccount_IdOrderByIdAsc(TagType.CUSTOM, accountId);
    }

    private Optional<Tag> getGlobalDefaultTagIfExists(Long tagId) {
        return tagRepository.findByIdAndTagTypeAndAccountIsNull(tagId, TagType.DEFAULT);
    }

    private Optional<Tag> getAccountCustomTagIfExists(Long accountId, Long tagId) {
        return tagRepository.findByIdAndTagTypeAndAccount_Id(tagId, TagType.CUSTOM, accountId);
    }
}
