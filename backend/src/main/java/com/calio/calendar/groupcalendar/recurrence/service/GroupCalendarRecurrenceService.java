package com.calio.calendar.groupcalendar.recurrence.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupcalendar.recurrence.controller.dto.GroupCalendarRecurrenceRequest;
import com.calio.calendar.groupcalendar.recurrence.controller.dto.GroupCalendarRecurrenceResponse;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceEvent;
import com.calio.calendar.groupcalendar.recurrence.repository.GroupCalendarRecurrenceEventRepository;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.groupspace.service.GroupSpaceCommandService;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GroupCalendarRecurrenceService {

    private final GroupSpaceCommandService groupSpaceCommandService;
    private final GroupMembershipQueryService membershipQueryService;
    private final AccountQueryService accountQueryService;
    private final TagRepository tagRepository;
    private final GroupCalendarRecurrenceEventRepository recurrenceRepository;
    private final Rfc5545RecurrenceEngine recurrenceEngine;

    public GroupCalendarRecurrenceService(GroupSpaceCommandService groupSpaceCommandService, GroupMembershipQueryService membershipQueryService, AccountQueryService accountQueryService, TagRepository tagRepository, GroupCalendarRecurrenceEventRepository recurrenceRepository, Rfc5545RecurrenceEngine recurrenceEngine) {
        this.groupSpaceCommandService = groupSpaceCommandService;
        this.membershipQueryService = membershipQueryService;
        this.accountQueryService = accountQueryService;
        this.tagRepository = tagRepository;
        this.recurrenceRepository = recurrenceRepository;
        this.recurrenceEngine = recurrenceEngine;
    }

    @Transactional
    public GroupCalendarRecurrenceResponse create(Long accountId, Long groupSpaceId, GroupCalendarRecurrenceRequest request) {
        GroupSpace groupSpace = groupSpaceCommandService.lockGroupSpace(groupSpaceId);
        membershipQueryService.getActiveMembership(groupSpaceId, accountId);
        RecurrenceSchedule schedule = schedule(request);
        GroupCalendarRecurrenceEvent event = new GroupCalendarRecurrenceEvent(groupSpace, accountQueryService.getAccount(accountId), tag(groupSpaceId, request.tagId()), request.title(), request.description(), schedule, recurrenceEngine.validate(schedule, request.recurrence()));
        return GroupCalendarRecurrenceResponse.from(recurrenceRepository.save(event));
    }

    public GroupCalendarRecurrenceResponse get(Long accountId, Long groupSpaceId, Long recurrenceId) {
        membershipQueryService.getActiveMembership(groupSpaceId, accountId);
        return GroupCalendarRecurrenceResponse.from(getEvent(groupSpaceId, recurrenceId));
    }

    @Transactional
    public GroupCalendarRecurrenceResponse update(Long accountId, Long groupSpaceId, Long recurrenceId, GroupCalendarRecurrenceRequest request) {
        GroupCalendarRecurrenceEvent event = lockEvent(groupSpaceId, recurrenceId);
        requireAuthorOrOwner(accountId, event);
        RecurrenceSchedule schedule = schedule(request);
        event.update(request.title(), request.description(), tag(groupSpaceId, request.tagId()), schedule, recurrenceEngine.validate(schedule, request.recurrence()));
        return GroupCalendarRecurrenceResponse.from(event);
    }

    @Transactional
    public void delete(Long accountId, Long groupSpaceId, Long recurrenceId) {
        GroupCalendarRecurrenceEvent event = lockEvent(groupSpaceId, recurrenceId);
        requireAuthorOrOwner(accountId, event);
        recurrenceRepository.delete(event);
    }

    private RecurrenceSchedule schedule(GroupCalendarRecurrenceRequest request) { return RecurrenceSchedule.create(request.allDay(), request.firstOccurrenceStartAt(), request.firstOccurrenceEndAt(), request.timeZone()); }
    private Tag tag(Long groupSpaceId, Long tagId) { return tagId == null ? tagRepository.findByTagTypeAndGroupSpace_Id(TagType.GROUP_DEFAULT, groupSpaceId).orElseThrow(() -> new CalioException(ErrorCode.GROUP_DEFAULT_TAG_NOT_FOUND)) : tagRepository.findByIdAndGroupSpace_Id(tagId, groupSpaceId).orElseThrow(() -> new CalioException(ErrorCode.GROUP_TAG_NOT_FOUND)); }
    private GroupCalendarRecurrenceEvent getEvent(Long groupSpaceId, Long id) { return recurrenceRepository.findByIdAndGroupSpace_Id(id, groupSpaceId).orElseThrow(() -> new CalioException(ErrorCode.GROUP_RECURRENCE_EVENT_NOT_FOUND)); }
    private GroupCalendarRecurrenceEvent lockEvent(Long groupSpaceId, Long id) { return recurrenceRepository.findByIdAndGroupSpaceIdForUpdate(id, groupSpaceId).orElseThrow(() -> new CalioException(ErrorCode.GROUP_RECURRENCE_EVENT_NOT_FOUND)); }
    private void requireAuthorOrOwner(Long accountId, GroupCalendarRecurrenceEvent event) { GroupMember member = membershipQueryService.getActiveMembership(event.getGroupSpace().getId(), accountId); if (!event.getCreatedBy().getId().equals(accountId) && !member.roleIn(event.getGroupSpace()).isOwner()) throw new CalioException(ErrorCode.GROUP_RECURRENCE_EVENT_FORBIDDEN); }
}
