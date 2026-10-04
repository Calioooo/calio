package com.calio.calendar.groupspace.usecase;

import com.calio.calendar.groupcalendar.event.repository.GroupCalendarEventRepository;
import com.calio.calendar.groupcalendar.recurrence.service.GroupCalendarRecurrenceCommandService;
import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.service.GroupScheduleShareCleanupPort;
import org.springframework.stereotype.Component;

@Component
class GroupMemberDepartureCleanup {

  private final GroupScheduleShareCleanupPort scheduleShareCleanupPort;
  private final GroupCalendarEventRepository groupCalendarEventRepository;
  private final GroupCalendarRecurrenceCommandService groupCalendarRecurrenceCommandService;
  private final GroupInvitationRepository invitationRepository;

  GroupMemberDepartureCleanup(
      GroupScheduleShareCleanupPort scheduleShareCleanupPort,
      GroupCalendarEventRepository groupCalendarEventRepository,
      GroupCalendarRecurrenceCommandService groupCalendarRecurrenceCommandService,
      GroupInvitationRepository invitationRepository) {
    this.scheduleShareCleanupPort = scheduleShareCleanupPort;
    this.groupCalendarEventRepository = groupCalendarEventRepository;
    this.groupCalendarRecurrenceCommandService = groupCalendarRecurrenceCommandService;
    this.invitationRepository = invitationRepository;
  }

  void clean(GroupMember member) {
    scheduleShareCleanupPort.cleanupMemberShares(member.getGroupSpaceId(), member.getId());
    groupCalendarEventRepository.deleteAllByGroupSpaceIdAndCreatedById(
        member.getGroupSpaceId(), member.getAccountId());
    groupCalendarRecurrenceCommandService.deleteAllCreatedByMember(
        member.getGroupSpaceId(), member.getAccountId());
    invitationRepository.deleteAllByCreatedByMemberId(member.getId());
  }
}
