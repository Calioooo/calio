package com.calio.calendar.groupcalendar.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupcalendar.controller.dto.GroupCalendarItemResponse;
import com.calio.calendar.groupcalendar.event.service.GroupCalendarEventQueryService;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceEvent;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceOccurrence;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceOverride;
import com.calio.calendar.groupcalendar.recurrence.service.GroupCalendarRecurrenceOccurrenceResolver;
import com.calio.calendar.groupcalendar.recurrence.service.GroupCalendarRecurrenceOverrideQueryService;
import com.calio.calendar.groupcalendar.recurrence.service.GroupCalendarRecurrenceQueryService;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GroupCalendarService {

  private static final Duration MAX_QUERY_RANGE = Duration.ofDays(366);

  private final GroupMembershipQueryService membershipQueryService;
  private final GroupCalendarEventQueryService eventQueryService;
  private final GroupCalendarRecurrenceQueryService recurrenceQueryService;
  private final GroupCalendarRecurrenceOverrideQueryService overrideQueryService;
  private final GroupCalendarRecurrenceOccurrenceResolver recurrenceOccurrenceResolver;

  public GroupCalendarService(
      GroupMembershipQueryService membershipQueryService,
      GroupCalendarEventQueryService eventQueryService,
      GroupCalendarRecurrenceQueryService recurrenceQueryService,
      GroupCalendarRecurrenceOverrideQueryService overrideQueryService,
      GroupCalendarRecurrenceOccurrenceResolver recurrenceOccurrenceResolver) {
    this.membershipQueryService = membershipQueryService;
    this.eventQueryService = eventQueryService;
    this.recurrenceQueryService = recurrenceQueryService;
    this.overrideQueryService = overrideQueryService;
    this.recurrenceOccurrenceResolver = recurrenceOccurrenceResolver;
  }

  public List<GroupCalendarItemResponse> listItems(
      Long accountId, Long groupSpaceId, Instant from, Instant to) {
    membershipQueryService.getActiveMembership(groupSpaceId, accountId);
    validateRange(from, to);
    Map<Long, String> nicknamesByAccountId = listNicknames(groupSpaceId);

    List<GroupCalendarItemResponse> items =
        new ArrayList<>(listDirectEvents(groupSpaceId, from, to, nicknamesByAccountId));
    items.addAll(listRecurrenceOccurrences(groupSpaceId, from, to, nicknamesByAccountId));
    items.sort(Comparator.comparing(GroupCalendarItemResponse::startAt));
    return items;
  }

  private List<GroupCalendarItemResponse> listDirectEvents(
      Long groupSpaceId, Instant from, Instant to, Map<Long, String> nicknamesByAccountId) {
    return eventQueryService.listOverlappingEvents(groupSpaceId, from, to).stream()
        .map(
            event ->
                GroupCalendarItemResponse.from(
                    event, nicknameOf(nicknamesByAccountId, event.getCreatedBy().getId())))
        .toList();
  }

  private List<GroupCalendarItemResponse> listRecurrenceOccurrences(
      Long groupSpaceId, Instant from, Instant to, Map<Long, String> nicknamesByAccountId) {
    List<GroupCalendarItemResponse> items = new ArrayList<>();
    Set<OccurrenceKey> occurrenceKeys = new HashSet<>();
    recurrenceQueryService
        .listExpansionCandidates(groupSpaceId, to)
        .forEach(
            recurrenceEvent ->
                addExpandedOccurrences(
                    recurrenceEvent,
                    from,
                    to,
                    occurrenceKeys,
                    items,
                    nicknameOf(nicknamesByAccountId, recurrenceEvent.getCreatedBy().getId())));
    addMovedInOverrides(groupSpaceId, from, to, occurrenceKeys, items, nicknamesByAccountId);
    return items;
  }

  private void addExpandedOccurrences(
      GroupCalendarRecurrenceEvent recurrenceEvent,
      Instant from,
      Instant to,
      Set<OccurrenceKey> occurrenceKeys,
      List<GroupCalendarItemResponse> items,
      String nickname) {
    List<RecurrenceOccurrence> occurrences =
        recurrenceOccurrenceResolver.expand(recurrenceEvent, from, to);
    Map<Instant, GroupCalendarRecurrenceOverride> overridesByOrigin =
        overridesByOrigin(recurrenceEvent, occurrences);
    recurrenceOccurrenceResolver
        .resolve(recurrenceEvent, occurrences, List.copyOf(overridesByOrigin.values()), from, to)
        .forEach(occurrence -> addResolvedOccurrence(occurrence, nickname, occurrenceKeys, items));
  }

  private Map<Instant, GroupCalendarRecurrenceOverride> overridesByOrigin(
      GroupCalendarRecurrenceEvent recurrenceEvent, List<RecurrenceOccurrence> occurrences) {
    List<Instant> origins = occurrences.stream().map(RecurrenceOccurrence::originStartAt).toList();
    if (origins.isEmpty()) {
      return Map.of();
    }
    return overrideQueryService.listOverrides(recurrenceEvent.getId(), origins).stream()
        .collect(
            Collectors.toMap(
                GroupCalendarRecurrenceOverride::getOriginStartAt, Function.identity()));
  }

  private void addResolvedOccurrence(
      GroupCalendarRecurrenceOccurrence occurrence,
      String nickname,
      Set<OccurrenceKey> occurrenceKeys,
      List<GroupCalendarItemResponse> items) {
    OccurrenceKey key =
        new OccurrenceKey(occurrence.recurrenceEvent().getId(), occurrence.originStartAt());
    if (occurrenceKeys.add(key)) {
      items.add(GroupCalendarItemResponse.recurrenceOccurrence(occurrence, nickname));
    }
  }

  private void addMovedInOverrides(
      Long groupSpaceId,
      Instant from,
      Instant to,
      Set<OccurrenceKey> occurrenceKeys,
      List<GroupCalendarItemResponse> items,
      Map<Long, String> nicknamesByAccountId) {
    recurrenceOccurrenceResolver
        .resolveMovedIn(overrideQueryService.listMovedInOverrides(groupSpaceId, from, to), from, to)
        .forEach(
            occurrence ->
                addResolvedOccurrence(
                    occurrence,
                    nicknameOf(
                        nicknamesByAccountId, occurrence.recurrenceEvent().getCreatedBy().getId()),
                    occurrenceKeys,
                    items));
  }

  private Map<Long, String> listNicknames(Long groupSpaceId) {
    return membershipQueryService.listActiveMembers(groupSpaceId).stream()
        .collect(Collectors.toMap(GroupMember::getAccountId, GroupMember::getNickname));
  }

  private String nicknameOf(Map<Long, String> nicknamesByAccountId, Long accountId) {
    return nicknamesByAccountId.get(accountId);
  }

  private void validateRange(Instant from, Instant to) {
    if (!from.isBefore(to)) {
      throw new CalioException(ErrorCode.INVALID_TIME_RANGE);
    }
    if (Duration.between(from, to).compareTo(MAX_QUERY_RANGE) > 0) {
      throw new CalioException(ErrorCode.EVENT_QUERY_RANGE_TOO_LARGE);
    }
  }

  private record OccurrenceKey(Long recurrenceId, Instant originStartAt) {}
}
