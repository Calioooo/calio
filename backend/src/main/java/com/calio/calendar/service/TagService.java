package com.calio.calendar.service;

import com.calio.calendar.controller.dto.CustomTagRequest;
import com.calio.calendar.controller.dto.TagResponse;
import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import com.calio.calendar.repository.EventRepository;
import com.calio.calendar.repository.RecurrenceEventRepository;
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
    private final EventRepository eventRepository;
    private final RecurrenceEventRepository recurrenceEventRepository;

    public TagService(
            TagRepository tagRepository,
            EventRepository eventRepository,
            RecurrenceEventRepository recurrenceEventRepository
    ) {
        this.tagRepository = tagRepository;
        this.eventRepository = eventRepository;
        this.recurrenceEventRepository = recurrenceEventRepository;
    }

    public List<TagResponse> listTags() {
        return tagRepository.findAllByOrderByIdAsc()
                .stream()
                .map(TagResponse::from)
                .toList();
    }

    public Tag getTagOrDefault(Long tagId) {
        if (tagId == null) {
            return resolveFallbackTag();
        }

        return getTag(tagId);
    }

    public Tag getTag(Long tagId) {
        return tagRepository.findById(tagId)
                .orElseThrow(() -> new CalioException(ErrorCode.TAG_NOT_FOUND));
    }

    @Transactional
    public TagResponse createCustomTag(CustomTagRequest request) {
        Tag tag = tagRepository.save(new Tag(TagType.CUSTOM, request.title(), request.colorCode()));
        return TagResponse.from(tag);
    }

    @Transactional
    public TagResponse updateCustomTag(Long tagId, CustomTagRequest request) {
        Tag tag = getCustomTag(tagId);
        tag.update(request.title(), request.colorCode());
        tagRepository.flush();
        return TagResponse.from(tag);
    }

    @Transactional
    public void deleteCustomTag(Long tagId) {
        Tag tag = getCustomTag(tagId);
        Tag fallbackTag = resolveFallbackTag();

        reassignEvents(tag, fallbackTag);
        reassignRecurrenceEvents(tag, fallbackTag);
        tagRepository.delete(tag);
    }

    private Tag getCustomTag(Long tagId) {
        return tagRepository.findByIdAndTagType(tagId, TagType.CUSTOM)
                .orElseThrow(() -> new CalioException(ErrorCode.TAG_NOT_FOUND));
    }

    private void reassignEvents(Tag sourceTag, Tag fallbackTag) {
        eventRepository.reassignAllByTag(sourceTag, fallbackTag);
    }

    private void reassignRecurrenceEvents(Tag sourceTag, Tag fallbackTag) {
        recurrenceEventRepository.reassignAllByTag(sourceTag, fallbackTag);
    }

    private Tag resolveFallbackTag() {
        return tagRepository.findFirstByTagTypeAndTitleOrderByIdAsc(TagType.DEFAULT, FALLBACK_TAG_TITLE)
                .orElseThrow(() -> new CalioException(ErrorCode.DEFAULT_TAG_NOT_FOUND));
    }
}
