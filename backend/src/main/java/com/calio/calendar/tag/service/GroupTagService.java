package com.calio.calendar.tag.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupcalendar.event.service.GroupCalendarEventCommandService;
import com.calio.calendar.groupcalendar.recurrence.service.GroupCalendarRecurrenceCommandService;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.groupspace.service.GroupSpaceQueryService;
import com.calio.calendar.tag.controller.dto.CustomTagRequest;
import com.calio.calendar.tag.controller.dto.TagResponse;
import com.calio.calendar.tag.domain.Tag;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GroupTagService {

    private final GroupSpaceQueryService groupSpaceQueryService;
    private final GroupMembershipQueryService membershipQueryService;
    private final TagQueryService tagQueryService;
    private final TagCommandService tagCommandService;
    private final GroupCalendarEventCommandService groupCalendarEventCommandService;
    private final GroupCalendarRecurrenceCommandService groupCalendarRecurrenceCommandService;

    public GroupTagService(
            GroupSpaceQueryService groupSpaceQueryService,
            GroupMembershipQueryService membershipQueryService,
            TagQueryService tagQueryService,
            TagCommandService tagCommandService,
            GroupCalendarEventCommandService groupCalendarEventCommandService,
            GroupCalendarRecurrenceCommandService groupCalendarRecurrenceCommandService
    ) {
        this.groupSpaceQueryService = groupSpaceQueryService;
        this.membershipQueryService = membershipQueryService;
        this.tagQueryService = tagQueryService;
        this.tagCommandService = tagCommandService;
        this.groupCalendarEventCommandService = groupCalendarEventCommandService;
        this.groupCalendarRecurrenceCommandService = groupCalendarRecurrenceCommandService;
    }

    public void createDefaultTag(GroupSpace groupSpace) {
        tagCommandService.createGroupDefaultTag(groupSpace);
    }

    @Transactional
    public void deleteAll(Long groupSpaceId) {
        tagCommandService.deleteGroupTags(groupSpaceId);
    }

    public List<TagResponse> list(Long accountId, Long groupSpaceId) {
        membershipQueryService.getActiveMembership(groupSpaceId, accountId);

        return tagQueryService.listGroupTags(groupSpaceId)
                .stream()
                .map(TagResponse::from)
                .toList();
    }

    @Transactional
    public TagResponse create(Long accountId, Long groupSpaceId, CustomTagRequest request) {
        GroupSpace groupSpace = groupSpaceQueryService.getGroupSpace(groupSpaceId);
        membershipQueryService.getActiveMembership(groupSpaceId, accountId);
        rejectDuplicateTitle(groupSpaceId, request.title());

        Tag tag = tagCommandService.createGroupCustomTag(groupSpace, request.title(), request.colorCode());
        return TagResponse.from(tag);
    }

    @Transactional
    public TagResponse update(Long accountId, Long groupSpaceId, Long tagId, CustomTagRequest request) {
        membershipQueryService.getActiveMembership(groupSpaceId, accountId);
        Tag tag = getCustomTag(groupSpaceId, tagId);
        rejectDuplicateTitle(groupSpaceId, tagId, request.title());

        return TagResponse.from(tagCommandService.updateCustomTag(tag, request.title(), request.colorCode()));
    }

    @Transactional
    public void delete(Long accountId, Long groupSpaceId, Long tagId) {
        membershipQueryService.getActiveMembership(groupSpaceId, accountId);
        Tag tag = getCustomTag(groupSpaceId, tagId);
        Tag fallbackTag = getDefaultTag(groupSpaceId);

        groupCalendarEventCommandService.changeTagForEvents(tag, fallbackTag);
        groupCalendarRecurrenceCommandService.changeTagForRecurrenceEvents(tag, fallbackTag);
        tagCommandService.deleteTag(tag);
    }

    private void rejectDuplicateTitle(Long groupSpaceId, String title) {
        rejectDuplicateTitle(groupSpaceId, null, title);
    }

    private void rejectDuplicateTitle(Long groupSpaceId, Long tagId, String title) {
        if (tagQueryService.hasGroupCustomTagTitle(groupSpaceId, title, tagId)) {
            throw new CalioException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private Tag getCustomTag(Long groupSpaceId, Long tagId) {
        return tagQueryService.getGroupCustomTag(groupSpaceId, tagId);
    }

    private Tag getDefaultTag(Long groupSpaceId) {
        return tagQueryService.getGroupDefaultTag(groupSpaceId);
    }
}
