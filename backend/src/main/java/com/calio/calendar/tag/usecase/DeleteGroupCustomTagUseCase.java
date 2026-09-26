package com.calio.calendar.tag.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupcalendar.event.service.GroupCalendarEventCommandService;
import com.calio.calendar.groupcalendar.recurrence.service.GroupCalendarRecurrenceCommandService;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteGroupCustomTagUseCase {

  private final GroupMembershipQueryService groupMembershipQueryService;
  private final GroupCalendarEventCommandService groupCalendarEventCommandService;
  private final GroupCalendarRecurrenceCommandService groupCalendarRecurrenceCommandService;
  private final TagRepository tagRepository;

  public DeleteGroupCustomTagUseCase(
      GroupMembershipQueryService groupMembershipQueryService,
      GroupCalendarEventCommandService groupCalendarEventCommandService,
      GroupCalendarRecurrenceCommandService groupCalendarRecurrenceCommandService,
      TagRepository tagRepository) {
    this.groupMembershipQueryService = groupMembershipQueryService;
    this.groupCalendarEventCommandService = groupCalendarEventCommandService;
    this.groupCalendarRecurrenceCommandService = groupCalendarRecurrenceCommandService;
    this.tagRepository = tagRepository;
  }

  @Transactional
  public void delete(Long accountId, Long groupSpaceId, Long tagId) {
    groupMembershipQueryService.getActiveMembership(groupSpaceId, accountId);
    Tag tag =
        tagRepository
            .findByIdAndGroupSpaceId(tagId, groupSpaceId)
            .filter(candidate -> candidate.getTagType() == TagType.CUSTOM)
            .orElseThrow(() -> new CalioException(ErrorCode.GROUP_TAG_NOT_FOUND));
    Tag fallbackTag =
        tagRepository
            .findGroupFallbackTag(groupSpaceId)
            .orElseThrow(() -> new CalioException(ErrorCode.GROUP_DEFAULT_TAG_NOT_FOUND));
    groupCalendarEventCommandService.changeTagForEvents(tag, fallbackTag);
    groupCalendarRecurrenceCommandService.changeTagForRecurrenceEvents(tag, fallbackTag);
    tagRepository.delete(tag);
  }
}
