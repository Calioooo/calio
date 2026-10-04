package com.calio.calendar.groupspace.usecase;

import com.calio.calendar.groupcalendar.event.repository.GroupCalendarEventRepository;
import com.calio.calendar.groupcalendar.recurrence.service.GroupCalendarRecurrenceCommandService;
import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import com.calio.calendar.groupspace.service.GroupScheduleShareCleanupPort;
import com.calio.calendar.tag.repository.TagRepository;
import org.springframework.stereotype.Component;

@Component
class GroupSpaceDeletionCleanup {

  private final GroupScheduleShareCleanupPort scheduleShareCleanupPort;
  private final GroupCalendarEventRepository groupCalendarEventRepository;
  private final GroupCalendarRecurrenceCommandService groupCalendarRecurrenceCommandService;
  private final GroupInvitationRepository invitationRepository;
  private final TagRepository tagRepository;
  private final GroupMemberRepository groupMemberRepository;
  private final GroupSpaceRepository groupSpaceRepository;

  GroupSpaceDeletionCleanup(
      GroupScheduleShareCleanupPort scheduleShareCleanupPort,
      GroupCalendarEventRepository groupCalendarEventRepository,
      GroupCalendarRecurrenceCommandService groupCalendarRecurrenceCommandService,
      GroupInvitationRepository invitationRepository,
      TagRepository tagRepository,
      GroupMemberRepository groupMemberRepository,
      GroupSpaceRepository groupSpaceRepository) {
    this.scheduleShareCleanupPort = scheduleShareCleanupPort;
    this.groupCalendarEventRepository = groupCalendarEventRepository;
    this.groupCalendarRecurrenceCommandService = groupCalendarRecurrenceCommandService;
    this.invitationRepository = invitationRepository;
    this.tagRepository = tagRepository;
    this.groupMemberRepository = groupMemberRepository;
    this.groupSpaceRepository = groupSpaceRepository;
  }

  void delete(GroupSpace groupSpace) {
    Long groupSpaceId = groupSpace.getId();
    scheduleShareCleanupPort.cleanupGroupShares(groupSpaceId);
    groupCalendarEventRepository.deleteAllByGroupSpaceId(groupSpaceId);
    groupCalendarRecurrenceCommandService.deleteAllInGroupSpace(groupSpaceId);
    invitationRepository.deleteAllByGroupSpaceId(groupSpaceId);
    tagRepository.deleteAll(tagRepository.findByGroupSpaceId(groupSpaceId));
    groupMemberRepository.deleteAllByGroupSpaceId(groupSpaceId);
    groupSpaceRepository.delete(groupSpace);
  }
}
