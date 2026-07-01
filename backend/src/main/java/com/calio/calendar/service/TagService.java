package com.calio.calendar.service;

import com.calio.calendar.controller.dto.TagResponse;
import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import com.calio.calendar.repository.TagRepository;
import com.calio.calendar.repository.entity.Tag;
import com.calio.calendar.repository.entity.TagType;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TagService {

    private static final String FALLBACK_TAG_TITLE = "기타";

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    public List<TagResponse> listDefaultTags() {
        return tagRepository.findByTagTypeOrderByIdAsc(TagType.DEFAULT)
                .stream()
                .map(TagResponse::from)
                .toList();
    }

    public Tag resolveDefaultTag(Long tagId) {
        if (tagId == null) {
            return resolveFallbackTag();
        }

        return tagRepository.findByIdAndTagType(tagId, TagType.DEFAULT)
                .orElseThrow(() -> new CalioException(ErrorCode.TAG_NOT_FOUND));
    }

    public Tag resolveExplicitDefaultTag(Long tagId) {
        return tagRepository.findByIdAndTagType(tagId, TagType.DEFAULT)
                .orElseThrow(() -> new CalioException(ErrorCode.TAG_NOT_FOUND));
    }

    private Tag resolveFallbackTag() {
        return tagRepository.findFirstByTagTypeAndTitleOrderByIdAsc(TagType.DEFAULT, FALLBACK_TAG_TITLE)
                .orElseThrow(() -> new CalioException(ErrorCode.DEFAULT_TAG_NOT_FOUND));
    }
}
