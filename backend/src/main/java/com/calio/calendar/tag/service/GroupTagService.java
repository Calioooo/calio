package com.calio.calendar.tag.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.groupspace.service.GroupSpaceQueryService;
import com.calio.calendar.groupcalendar.event.service.GroupCalendarEventCommandService;
import com.calio.calendar.tag.controller.dto.CustomTagRequest;
import com.calio.calendar.tag.controller.dto.TagResponse;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GroupTagService {
    private final GroupSpaceQueryService groupSpaceQueryService;
    private final GroupMembershipQueryService membershipQueryService;
    private final TagRepository tagRepository;
    private final TagCommandService tagCommandService;
    private final GroupCalendarEventCommandService groupCalendarEventCommandService;
    public GroupTagService(GroupSpaceQueryService groupSpaceQueryService, GroupMembershipQueryService membershipQueryService, TagRepository tagRepository, TagCommandService tagCommandService, GroupCalendarEventCommandService groupCalendarEventCommandService) { this.groupSpaceQueryService = groupSpaceQueryService; this.membershipQueryService = membershipQueryService; this.tagRepository = tagRepository; this.tagCommandService = tagCommandService; this.groupCalendarEventCommandService = groupCalendarEventCommandService; }
    public void createDefaultTag(GroupSpace groupSpace) { tagCommandService.createGroupDefaultTag(groupSpace); }
    @Transactional public void deleteAll(Long groupSpaceId) { tagRepository.deleteAll(tagRepository.findByGroupSpace_Id(groupSpaceId)); }
    public List<TagResponse> list(Long accountId, Long groupSpaceId) { membershipQueryService.getActiveMembership(groupSpaceId, accountId); return tagRepository.findByGroupSpace_IdOrderByIdAsc(groupSpaceId).stream().map(TagResponse::from).toList(); }
    @Transactional public TagResponse create(Long accountId, Long groupSpaceId, CustomTagRequest request) { GroupSpace groupSpace = groupSpaceQueryService.getGroupSpace(groupSpaceId); membershipQueryService.getActiveMembership(groupSpaceId, accountId); rejectDuplicateTitle(groupSpaceId, request.title()); return TagResponse.from(tagCommandService.createGroupCustomTag(groupSpace, request.title(), request.colorCode())); }
    @Transactional public TagResponse update(Long accountId, Long groupSpaceId, Long tagId, CustomTagRequest request) { membershipQueryService.getActiveMembership(groupSpaceId, accountId); Tag tag = getCustomTag(groupSpaceId, tagId); rejectDuplicateTitle(groupSpaceId, tagId, request.title()); return TagResponse.from(tagCommandService.updateCustomTag(tag, request.title(), request.colorCode())); }
    @Transactional public void delete(Long accountId, Long groupSpaceId, Long tagId) { membershipQueryService.getActiveMembership(groupSpaceId, accountId); Tag tag = getCustomTag(groupSpaceId, tagId); Tag fallbackTag = getDefaultTag(groupSpaceId); groupCalendarEventCommandService.changeTagForEvents(tag, fallbackTag); tagCommandService.deleteTag(tag); }
    private void rejectDuplicateTitle(Long groupSpaceId, String title) { if (tagRepository.existsByTagTypeAndTitleAndGroupSpace_Id(TagType.CUSTOM, title, groupSpaceId)) throw new CalioException(ErrorCode.VALIDATION_FAILED); }
    private void rejectDuplicateTitle(Long groupSpaceId, Long tagId, String title) { if (tagRepository.existsByTagTypeAndTitleAndGroupSpace_IdAndIdNot(TagType.CUSTOM, title, groupSpaceId, tagId)) throw new CalioException(ErrorCode.VALIDATION_FAILED); }
    private Tag getCustomTag(Long groupSpaceId, Long tagId) { return tagRepository.findByIdAndGroupSpace_Id(tagId, groupSpaceId).filter(tag -> tag.getTagType() == TagType.CUSTOM).orElseThrow(() -> new CalioException(ErrorCode.GROUP_TAG_NOT_FOUND)); }
    private Tag getDefaultTag(Long groupSpaceId) { return tagRepository.findByTagTypeAndGroupSpace_Id(TagType.GROUP_DEFAULT, groupSpaceId).orElseThrow(() -> new CalioException(ErrorCode.GROUP_DEFAULT_TAG_NOT_FOUND)); }
}
