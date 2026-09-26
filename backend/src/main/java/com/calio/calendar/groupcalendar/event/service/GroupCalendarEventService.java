package com.calio.calendar.groupcalendar.event.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupcalendar.event.controller.dto.GroupCalendarEventRequest;
import com.calio.calendar.groupcalendar.event.controller.dto.GroupCalendarEventResponse;
import com.calio.calendar.groupcalendar.event.domain.GroupCalendarEvent;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.groupspace.service.GroupSpaceCommandService;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GroupCalendarEventService {

  private final GroupSpaceCommandService groupSpaceCommandService;
  private final GroupMembershipQueryService membershipQueryService;
  private final AccountRepository accountRepository;
  private final TagRepository tagRepository;
  private final GroupCalendarEventQueryService eventQueryService;
  private final GroupCalendarEventCommandService eventCommandService;

  public GroupCalendarEventService(
      GroupSpaceCommandService groupSpaceCommandService,
      GroupMembershipQueryService membershipQueryService,
      AccountRepository accountRepository,
      TagRepository tagRepository,
      GroupCalendarEventQueryService eventQueryService,
      GroupCalendarEventCommandService eventCommandService) {
    this.groupSpaceCommandService = groupSpaceCommandService;
    this.membershipQueryService = membershipQueryService;
    this.accountRepository = accountRepository;
    this.tagRepository = tagRepository;
    this.eventQueryService = eventQueryService;
    this.eventCommandService = eventCommandService;
  }

  @Transactional
  public GroupCalendarEventResponse create(
      Long accountId, Long groupSpaceId, GroupCalendarEventRequest request) {
    GroupSpace groupSpace = groupSpaceCommandService.lockGroupSpace(groupSpaceId);
    membershipQueryService.getActiveMembership(groupSpaceId, accountId);

    CanonicalSchedule schedule =
        CanonicalSchedule.event(
            request.startAt(), request.endAt(), request.allDay(), request.timeZone());
    Account account = getAccount(accountId);
    Tag tag = getTag(groupSpaceId, request.tagId());
    GroupCalendarEvent event =
        new GroupCalendarEvent(
            groupSpace,
            account,
            tag,
            request.title(),
            request.description(),
            schedule.startAt(),
            schedule.endAt(),
            schedule.allDay(),
            schedule.timeZone());

    return GroupCalendarEventResponse.from(eventCommandService.createEvent(event));
  }

  private Account getAccount(Long accountId) {
    return accountRepository
        .findById(accountId)
        .orElseThrow(() -> new CalioException(ErrorCode.INTERNAL_SERVER_ERROR));
  }

  public GroupCalendarEventResponse get(Long accountId, Long groupSpaceId, Long eventId) {
    membershipQueryService.getActiveMembership(groupSpaceId, accountId);
    return GroupCalendarEventResponse.from(eventQueryService.getEvent(groupSpaceId, eventId));
  }

  public List<GroupCalendarEventResponse> list(
      Long accountId, Long groupSpaceId, Instant from, Instant to) {
    membershipQueryService.getActiveMembership(groupSpaceId, accountId);
    validateRange(from, to);

    return eventQueryService.listOverlappingEvents(groupSpaceId, from, to).stream()
        .map(GroupCalendarEventResponse::from)
        .toList();
  }

  @Transactional
  public GroupCalendarEventResponse update(
      Long accountId, Long groupSpaceId, Long eventId, GroupCalendarEventRequest request) {
    GroupCalendarEvent event = getLockedEvent(groupSpaceId, eventId);
    requireAuthorOrOwner(accountId, event);

    CanonicalSchedule schedule =
        CanonicalSchedule.event(
            request.startAt(), request.endAt(), request.allDay(), request.timeZone());
    event.replace(
        request.title(),
        request.description(),
        schedule.startAt(),
        schedule.endAt(),
        schedule.allDay(),
        schedule.timeZone(),
        getTag(groupSpaceId, request.tagId()));

    return GroupCalendarEventResponse.from(event);
  }

  @Transactional
  public void delete(Long accountId, Long groupSpaceId, Long eventId) {
    GroupCalendarEvent event = getLockedEvent(groupSpaceId, eventId);
    requireAuthorOrOwner(accountId, event);
    eventCommandService.deleteEvent(event);
  }

  private GroupCalendarEvent getLockedEvent(Long groupSpaceId, Long eventId) {
    return eventCommandService.lockEvent(groupSpaceId, eventId);
  }

  private Tag getTag(Long groupSpaceId, Long tagId) {
    if (tagId == null) {
      return tagRepository
          .findGroupFallbackTag(groupSpaceId)
          .orElseThrow(() -> new CalioException(ErrorCode.GROUP_DEFAULT_TAG_NOT_FOUND));
    }
    return tagRepository
        .findByIdAndGroupSpaceId(tagId, groupSpaceId)
        .orElseThrow(() -> new CalioException(ErrorCode.GROUP_TAG_NOT_FOUND));
  }

  private void requireAuthorOrOwner(Long accountId, GroupCalendarEvent event) {
    GroupMember member =
        membershipQueryService.getActiveMembership(event.getGroupSpace().getId(), accountId);
    boolean isAuthor = event.getCreatedBy().getId().equals(accountId);

    if (!isAuthor && !member.roleIn(event.getGroupSpace()).isOwner()) {
      throw new CalioException(ErrorCode.GROUP_EVENT_FORBIDDEN);
    }
  }

  private void validateRange(Instant from, Instant to) {
    if (!from.isBefore(to)) {
      throw new CalioException(ErrorCode.INVALID_TIME_RANGE);
    }
  }
}
