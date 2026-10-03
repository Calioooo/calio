package com.calio.calendar.groupspace.usecase;

import com.calio.calendar.groupcalendar.event.repository.GroupCalendarEventRepository;
import com.calio.calendar.groupcalendar.recurrence.repository.GroupCalendarRecurrenceEventRepository;
import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.service.GroupScheduleShareCleanupPort;
import org.springframework.stereotype.Component;

@Component
class GroupMemberDepartureCleanup {

  private final GroupScheduleShareCleanupPort scheduleShareCleanupPort;
  private final GroupCalendarEventRepository groupCalendarEventRepository;
  private final GroupCalendarRecurrenceEventRepository groupCalendarRecurrenceEventRepository;
  private final GroupInvitationRepository invitationRepository;

  GroupMemberDepartureCleanup(
      GroupScheduleShareCleanupPort scheduleShareCleanupPort,
      GroupCalendarEventRepository groupCalendarEventRepository,
      GroupCalendarRecurrenceEventRepository groupCalendarRecurrenceEventRepository,
      GroupInvitationRepository invitationRepository) {
    this.scheduleShareCleanupPort = scheduleShareCleanupPort;
    this.groupCalendarEventRepository = groupCalendarEventRepository;
    this.groupCalendarRecurrenceEventRepository = groupCalendarRecurrenceEventRepository;
    this.invitationRepository = invitationRepository;
  }

  void clean(GroupMember member) {
    scheduleShareCleanupPort.cleanupMemberShares(member.getGroupSpaceId(), member.getId());
    groupCalendarEventRepository.deleteAllByGroupSpaceIdAndCreatedById(
        member.getGroupSpaceId(), member.getAccountId());
    groupCalendarRecurrenceEventRepository.deleteAllByGroupSpace_IdAndCreatedBy_Id(
        member.getGroupSpaceId(), member.getAccountId());
    invitationRepository.deleteAllByCreatedByMemberId(member.getId());
  }
}
