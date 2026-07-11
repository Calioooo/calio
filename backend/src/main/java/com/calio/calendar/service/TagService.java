package com.calio.calendar.service;

import com.calio.calendar.controller.dto.CustomTagRequest;
import com.calio.calendar.controller.dto.TagResponse;
import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import com.calio.calendar.repository.AccountRepository;
import com.calio.calendar.repository.EventRepository;
import com.calio.calendar.repository.RecurrenceEventRepository;
import com.calio.calendar.repository.TagRepository;
import com.calio.calendar.repository.entity.Account;
import com.calio.calendar.repository.entity.Tag;
import com.calio.calendar.repository.entity.TagType;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TagService {

    private static final String FALLBACK_TAG_TITLE = "기타";

    private final TagRepository tagRepository;
    private final AccountRepository accountRepository;
    private final EventRepository eventRepository;
    private final RecurrenceEventRepository recurrenceEventRepository;

    public TagService(
            TagRepository tagRepository,
            AccountRepository accountRepository,
            EventRepository eventRepository,
            RecurrenceEventRepository recurrenceEventRepository
    ) {
        this.tagRepository = tagRepository;
        this.accountRepository = accountRepository;
        this.eventRepository = eventRepository;
        this.recurrenceEventRepository = recurrenceEventRepository;
    }

    public List<TagResponse> listTags(Long accountId) {
        return Stream.concat(
                        findGlobalDefaultTags().stream(),
                        findAccountCustomTags(accountId).stream()
                )
                .sorted(Comparator.comparing(Tag::getId))
                .map(TagResponse::from)
                .toList();
    }

    public Tag getTagOrDefault(Long accountId, Long tagId) {
        if (tagId == null) {
            return resolveFallbackTag();
        }

        return getTag(accountId, tagId);
    }

    public Tag getTag(Long accountId, Long tagId) {
        return findGlobalDefaultTag(tagId)
                .or(() -> findAccountCustomTag(accountId, tagId))
                .orElseThrow(() -> new CalioException(ErrorCode.TAG_NOT_FOUND));
    }

    @Transactional
    public TagResponse createCustomTag(Long accountId, CustomTagRequest request) {
        Account account = accountRepository.getReferenceById(accountId);
        Tag tag = tagRepository.save(new Tag(TagType.CUSTOM, request.title(), request.colorCode(), account));
        return TagResponse.from(tag);
    }

    @Transactional
    public TagResponse updateCustomTag(Long accountId, Long tagId, CustomTagRequest request) {
        Tag tag = getCustomTag(accountId, tagId);
        tag.update(request.title(), request.colorCode());
        tagRepository.flush();
        return TagResponse.from(tag);
    }

    @Transactional
    public void deleteCustomTag(Long accountId, Long tagId) {
        Tag tag = getCustomTag(accountId, tagId);
        Tag fallbackTag = resolveFallbackTag();

        reassignEvents(accountId, tag, fallbackTag);
        reassignRecurrenceEvents(accountId, tag, fallbackTag);
        tagRepository.delete(tag);
    }

    private Tag getCustomTag(Long accountId, Long tagId) {
        return tagRepository.findByIdAndTagTypeAndAccount_Id(tagId, TagType.CUSTOM, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.TAG_NOT_FOUND));
    }

    private List<Tag> findGlobalDefaultTags() {
        return tagRepository.findByTagTypeAndAccountIsNullOrderByIdAsc(TagType.DEFAULT);
    }

    private List<Tag> findAccountCustomTags(Long accountId) {
        return tagRepository.findByTagTypeAndAccount_IdOrderByIdAsc(TagType.CUSTOM, accountId);
    }

    private Optional<Tag> findGlobalDefaultTag(Long tagId) {
        return tagRepository.findByIdAndTagTypeAndAccountIsNull(tagId, TagType.DEFAULT);
    }

    private Optional<Tag> findAccountCustomTag(Long accountId, Long tagId) {
        return tagRepository.findByIdAndTagTypeAndAccount_Id(tagId, TagType.CUSTOM, accountId);
    }

    private void reassignEvents(Long accountId, Tag sourceTag, Tag fallbackTag) {
        eventRepository.reassignAllByTagAndAccountId(sourceTag, fallbackTag, accountId);
    }

    private void reassignRecurrenceEvents(Long accountId, Tag sourceTag, Tag fallbackTag) {
        recurrenceEventRepository.reassignAllByTagAndAccountId(sourceTag, fallbackTag, accountId);
    }

    private Tag resolveFallbackTag() {
        return tagRepository.findFirstByTagTypeAndTitleAndAccountIsNullOrderByIdAsc(
                        TagType.DEFAULT,
                        FALLBACK_TAG_TITLE
                )
                .orElseThrow(() -> new CalioException(ErrorCode.DEFAULT_TAG_NOT_FOUND));
    }
}
