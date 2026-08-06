package com.calio.calendar.tag.service;

import com.calio.calendar.tag.controller.dto.CustomTagRequest;
import com.calio.calendar.tag.controller.dto.TagResponse;
import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.account.domain.Account;
import com.calio.calendar.event.service.EventCommandService;
import com.calio.calendar.recurrence.service.RecurrenceEventCommandService;
import com.calio.calendar.tag.domain.Tag;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TagService {

    private final TagQueryService tagQueryService;
    private final TagCommandService tagCommandService;
    private final AccountQueryService accountQueryService;
    private final EventCommandService eventCommandService;
    private final RecurrenceEventCommandService recurrenceEventCommandService;

    public TagService(
            TagQueryService tagQueryService,
            TagCommandService tagCommandService,
            AccountQueryService accountQueryService,
            EventCommandService eventCommandService,
            RecurrenceEventCommandService recurrenceEventCommandService
    ) {
        this.tagQueryService = tagQueryService;
        this.tagCommandService = tagCommandService;
        this.accountQueryService = accountQueryService;
        this.eventCommandService = eventCommandService;
        this.recurrenceEventCommandService = recurrenceEventCommandService;
    }

    public List<TagResponse> listTags(Long accountId) {
        return tagQueryService.listAvailableTags(accountId).stream()
                .map(TagResponse::from)
                .toList();
    }

    public Tag getTagOrDefault(Long accountId, Long tagId) {
        return tagQueryService.getTagOrDefault(accountId, tagId);
    }

    public Tag getTag(Long accountId, Long tagId) {
        return tagQueryService.getTag(accountId, tagId);
    }

    @Transactional
    public TagResponse createCustomTag(Long accountId, CustomTagRequest request) {
        Account account = accountQueryService.getAccount(accountId);
        Tag tag = tagCommandService.createCustomTag(account, request.title(), request.colorCode());
        return TagResponse.from(tag);
    }

    @Transactional
    public TagResponse updateCustomTag(Long accountId, Long tagId, CustomTagRequest request) {
        Tag tag = tagQueryService.getCustomTag(accountId, tagId);
        tagCommandService.updateCustomTag(tag, request.title(), request.colorCode());
        return TagResponse.from(tag);
    }

    @Transactional
    public void deleteCustomTag(Long accountId, Long tagId) {
        Tag tag = tagQueryService.getCustomTag(accountId, tagId);
        Tag fallbackTag = tagQueryService.getFallbackTag();

        eventCommandService.changeTagForTargetEvents(accountId, tag, fallbackTag);
        recurrenceEventCommandService.changeTagForRecurrenceEvents(accountId, tag, fallbackTag);
        tagCommandService.deleteTag(tag);
    }
}
