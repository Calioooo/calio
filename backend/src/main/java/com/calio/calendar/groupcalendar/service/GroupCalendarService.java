package com.calio.calendar.groupcalendar.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupcalendar.controller.dto.GroupCalendarItemResponse;
import com.calio.calendar.groupcalendar.event.domain.GroupCalendarEvent;
import com.calio.calendar.groupcalendar.event.service.GroupCalendarEventQueryService;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceEvent;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceOverride;
import com.calio.calendar.groupcalendar.recurrence.service.GroupCalendarRecurrenceOverrideQueryService;
import com.calio.calendar.groupcalendar.recurrence.service.GroupCalendarRecurrenceQueryService;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
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
    private final Rfc5545RecurrenceEngine recurrenceEngine;

    public GroupCalendarService(
            GroupMembershipQueryService membershipQueryService,
            GroupCalendarEventQueryService eventQueryService,
            GroupCalendarRecurrenceQueryService recurrenceQueryService,
            GroupCalendarRecurrenceOverrideQueryService overrideQueryService,
            Rfc5545RecurrenceEngine recurrenceEngine
    ) {
        this.membershipQueryService = membershipQueryService;
        this.eventQueryService = eventQueryService;
        this.recurrenceQueryService = recurrenceQueryService;
        this.overrideQueryService = overrideQueryService;
        this.recurrenceEngine = recurrenceEngine;
    }

    public List<GroupCalendarItemResponse> listItems(
            Long accountId,
            Long groupSpaceId,
            Instant from,
            Instant to
    ) {
        membershipQueryService.getActiveMembership(groupSpaceId, accountId);
        validateRange(from, to);

        List<GroupCalendarItemResponse> items = new ArrayList<>(listDirectEvents(groupSpaceId, from, to));
        items.addAll(listRecurrenceOccurrences(groupSpaceId, from, to));
        items.sort(Comparator.comparing(GroupCalendarItemResponse::startAt));
        return items;
    }

    private List<GroupCalendarItemResponse> listDirectEvents(Long groupSpaceId, Instant from, Instant to) {
        return eventQueryService.listOverlappingEvents(groupSpaceId, from, to)
                .stream()
                .map(event -> GroupCalendarItemResponse.from(event, creatorNickname(event)))
                .toList();
    }

    private List<GroupCalendarItemResponse> listRecurrenceOccurrences(
            Long groupSpaceId,
            Instant from,
            Instant to
    ) {
        List<GroupCalendarItemResponse> items = new ArrayList<>();
        Set<OccurrenceKey> occurrenceKeys = new HashSet<>();
        recurrenceQueryService.listExpansionCandidates(groupSpaceId, to).forEach(recurrenceEvent ->
                addExpandedOccurrences(recurrenceEvent, from, to, occurrenceKeys, items)
        );
        addMovedInOverrides(groupSpaceId, from, to, occurrenceKeys, items);
        return items;
    }

    private void addExpandedOccurrences(
            GroupCalendarRecurrenceEvent recurrenceEvent,
            Instant from,
            Instant to,
            Set<OccurrenceKey> occurrenceKeys,
            List<GroupCalendarItemResponse> items
    ) {
        List<RecurrenceOccurrence> occurrences = recurrenceEngine.expand(
                scheduleOf(recurrenceEvent),
                recurrenceEvent.getRecurrenceRules(),
                from,
                to
        );
        Map<Instant, GroupCalendarRecurrenceOverride> overridesByOrigin = overridesByOrigin(
                recurrenceEvent,
                occurrences
        );
        for (RecurrenceOccurrence occurrence : occurrences) {
            OccurrenceKey key = new OccurrenceKey(recurrenceEvent.getId(), occurrence.originStartAt());
            GroupCalendarItemResponse item = occurrenceItem(
                    recurrenceEvent,
                    occurrence,
                    overridesByOrigin.get(occurrence.originStartAt())
            );
            if (item != null && overlaps(item, from, to) && occurrenceKeys.add(key)) {
                items.add(item);
            }
        }
    }

    private Map<Instant, GroupCalendarRecurrenceOverride> overridesByOrigin(
            GroupCalendarRecurrenceEvent recurrenceEvent,
            List<RecurrenceOccurrence> occurrences
    ) {
        List<Instant> origins = occurrences.stream().map(RecurrenceOccurrence::originStartAt).toList();
        if (origins.isEmpty()) {
            return Map.of();
        }
        return overrideQueryService.listOverrides(recurrenceEvent.getId(), origins)
                .stream()
                .collect(Collectors.toMap(GroupCalendarRecurrenceOverride::getOriginStartAt, Function.identity()));
    }

    private GroupCalendarItemResponse occurrenceItem(
            GroupCalendarRecurrenceEvent recurrenceEvent,
            RecurrenceOccurrence occurrence,
            GroupCalendarRecurrenceOverride override
    ) {
        String nickname = creatorNickname(recurrenceEvent);
        if (override == null) {
            return GroupCalendarItemResponse.recurrenceOccurrence(recurrenceEvent, occurrence, nickname);
        }
        return override.isDeleted() ? null : GroupCalendarItemResponse.recurrenceOverride(override, nickname);
    }

    private void addMovedInOverrides(
            Long groupSpaceId,
            Instant from,
            Instant to,
            Set<OccurrenceKey> occurrenceKeys,
            List<GroupCalendarItemResponse> items
    ) {
        overrideQueryService.listMovedInOverrides(groupSpaceId, from, to)
                .stream()
                .filter(override -> occurrenceKeys.add(OccurrenceKey.from(override)))
                .map(override -> GroupCalendarItemResponse.recurrenceOverride(
                        override,
                        creatorNickname(override.getRecurrenceEvent())
                ))
                .forEach(items::add);
    }

    private RecurrenceSchedule scheduleOf(GroupCalendarRecurrenceEvent recurrenceEvent) {
        return new RecurrenceSchedule(
                recurrenceEvent.getFirstOccurrenceStartAt(),
                recurrenceEvent.getFirstOccurrenceEndAt(),
                recurrenceEvent.isAllDay(),
                recurrenceEvent.getTimeZone()
        );
    }

    private String creatorNickname(GroupCalendarEvent event) {
        return membershipQueryService.getActiveMembership(
                event.getGroupSpace().getId(),
                event.getCreatedBy().getId()
        ).getNickname();
    }

    private String creatorNickname(GroupCalendarRecurrenceEvent event) {
        return membershipQueryService.getActiveMembership(
                event.getGroupSpace().getId(),
                event.getCreatedBy().getId()
        ).getNickname();
    }

    private boolean overlaps(GroupCalendarItemResponse item, Instant from, Instant to) {
        return item.startAt().isBefore(to) && item.endAt().isAfter(from);
    }

    private void validateRange(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new CalioException(ErrorCode.INVALID_TIME_RANGE);
        }
        if (Duration.between(from, to).compareTo(MAX_QUERY_RANGE) > 0) {
            throw new CalioException(ErrorCode.EVENT_QUERY_RANGE_TOO_LARGE);
        }
    }

    private record OccurrenceKey(Long recurrenceId, Instant originStartAt) {
        private static OccurrenceKey from(GroupCalendarRecurrenceOverride override) {
            return new OccurrenceKey(override.getRecurrenceEvent().getId(), override.getOriginStartAt());
        }
    }
}
