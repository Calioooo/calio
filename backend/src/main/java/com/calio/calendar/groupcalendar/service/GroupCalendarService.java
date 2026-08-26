package com.calio.calendar.groupcalendar.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupcalendar.controller.dto.GroupCalendarItemResponse;
import com.calio.calendar.groupcalendar.event.service.GroupCalendarEventQueryService;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceEvent;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceOverride;
import com.calio.calendar.groupcalendar.recurrence.service.GroupCalendarRecurrenceOverrideQueryService;
import com.calio.calendar.groupcalendar.recurrence.service.GroupCalendarRecurrenceQueryService;
import com.calio.calendar.groupcalendar.sharing.event.service.PersonalEventGroupShareQueryService;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareOccurrenceOverride;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareScope;
import com.calio.calendar.groupcalendar.sharing.recurrence.service.PersonalRecurrenceGroupShareQueryService;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.service.RecurrenceEventQueryService;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private final PersonalEventGroupShareQueryService personalEventShareQueryService;
    private final PersonalRecurrenceGroupShareQueryService personalRecurrenceShareQueryService;
    private final RecurrenceEventQueryService personalRecurrenceQueryService;
    private final Rfc5545RecurrenceEngine recurrenceEngine;

    public GroupCalendarService(
            GroupMembershipQueryService membershipQueryService,
            GroupCalendarEventQueryService eventQueryService,
            GroupCalendarRecurrenceQueryService recurrenceQueryService,
            GroupCalendarRecurrenceOverrideQueryService overrideQueryService,
            PersonalEventGroupShareQueryService personalEventShareQueryService,
            PersonalRecurrenceGroupShareQueryService personalRecurrenceShareQueryService,
            RecurrenceEventQueryService personalRecurrenceQueryService,
            Rfc5545RecurrenceEngine recurrenceEngine
    ) {
        this.membershipQueryService = membershipQueryService;
        this.eventQueryService = eventQueryService;
        this.recurrenceQueryService = recurrenceQueryService;
        this.overrideQueryService = overrideQueryService;
        this.personalEventShareQueryService = personalEventShareQueryService;
        this.personalRecurrenceShareQueryService = personalRecurrenceShareQueryService;
        this.personalRecurrenceQueryService = personalRecurrenceQueryService;
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
        Map<Long, String> nicknamesByAccountId = listNicknames(groupSpaceId);

        List<GroupCalendarItemResponse> items = new ArrayList<>(
                listDirectEvents(groupSpaceId, from, to, nicknamesByAccountId)
        );
        items.addAll(listSharedOneOffEvents(groupSpaceId, from, to, nicknamesByAccountId));
        items.addAll(listRecurrenceOccurrences(groupSpaceId, from, to, nicknamesByAccountId));
        items.addAll(listSharedRecurrenceOccurrences(groupSpaceId, from, to, nicknamesByAccountId));
        return mergeItems(items);
    }

    private List<GroupCalendarItemResponse> listSharedRecurrenceOccurrences(
            Long groupSpaceId,
            Instant from,
            Instant to,
            Map<Long, String> nicknamesByAccountId
    ) {
        List<GroupCalendarItemResponse> items = new ArrayList<>();
        personalRecurrenceShareQueryService.listSharesInGroupSpace(groupSpaceId).forEach(share ->
                addSharedRecurrenceOccurrences(share, from, to, nicknamesByAccountId, items)
        );
        return items;
    }

    private void addSharedRecurrenceOccurrences(
            PersonalRecurrenceGroupShare share,
            Instant from,
            Instant to,
            Map<Long, String> nicknamesByAccountId,
            List<GroupCalendarItemResponse> items
    ) {
        RecurrenceEvent recurrenceEvent = share.getRecurrenceEvent();
        List<RecurrenceOccurrence> occurrences = recurrenceEngine.expand(
                com.calio.calendar.recurrence.domain.RecurrenceSchedule.from(recurrenceEvent),
                recurrenceEvent.getRecurrenceRules(),
                from,
                to
        );
        Map<Instant, RecurrenceEventOverride> sourceOverrides = sourceOverridesByOrigin(
                recurrenceEvent,
                occurrences
        );
        Set<SharedOccurrenceKey> occurrenceKeys = new HashSet<>();
        occurrences.forEach(occurrence -> addSharedOccurrence(
                share,
                occurrence,
                sourceOverrides.get(occurrence.originStartAt()),
                from,
                to,
                nicknamesByAccountId,
                occurrenceKeys,
                items
        ));
        personalRecurrenceQueryService.listActiveOverlappingOverridesForRecurrence(
                        recurrenceEvent.getId(), from, to
                )
                .forEach(override -> addSharedOccurrence(
                        share,
                        new RecurrenceOccurrence(
                                override.getOriginStartAt(),
                                override.getOriginStartAt(),
                                override.getOverrideEndAt()
                        ),
                        override,
                        from,
                        to,
                        nicknamesByAccountId,
                        occurrenceKeys,
                        items
                ));
    }

    private Map<Instant, RecurrenceEventOverride> sourceOverridesByOrigin(
            RecurrenceEvent recurrenceEvent,
            List<RecurrenceOccurrence> occurrences
    ) {
        List<Instant> origins = occurrences.stream().map(RecurrenceOccurrence::originStartAt).toList();
        if (origins.isEmpty()) {
            return Map.of();
        }
        return personalRecurrenceQueryService.listOverrides(recurrenceEvent.getId(), origins)
                .stream()
                .collect(Collectors.toMap(RecurrenceEventOverride::getOriginStartAt, Function.identity()));
    }

    private void addSharedOccurrence(
            PersonalRecurrenceGroupShare share,
            RecurrenceOccurrence occurrence,
            RecurrenceEventOverride sourceOverride,
            Instant from,
            Instant to,
            Map<Long, String> nicknamesByAccountId,
            Set<SharedOccurrenceKey> occurrenceKeys,
            List<GroupCalendarItemResponse> items
    ) {
        SharedRecurrenceOccurrence sourceOccurrence = sourceOccurrence(
                share.getRecurrenceEvent(),
                occurrence,
                sourceOverride
        );
        if (sourceOccurrence == null) {
            return;
        }
        GroupCalendarItemResponse item = sharedRecurrenceItem(
                share,
                sourceOccurrence,
                nicknamesByAccountId
        );
        SharedOccurrenceKey key = new SharedOccurrenceKey(share.getId(), occurrence.originStartAt());
        if (overlaps(item, from, to) && occurrenceKeys.add(key)) {
            items.add(item);
        }
    }

    private SharedRecurrenceOccurrence sourceOccurrence(
            RecurrenceEvent recurrenceEvent,
            RecurrenceOccurrence occurrence,
            RecurrenceEventOverride sourceOverride
    ) {
        if (sourceOverride == null) {
            return new SharedRecurrenceOccurrence(
                    occurrence.originStartAt(),
                    recurrenceEvent.getTitle(),
                    recurrenceEvent.getDescription(),
                    occurrence.startAt(),
                    occurrence.endAt(),
                    recurrenceEvent.isAllDay(),
                    recurrenceEvent.getTimeZone()
            );
        }
        if (sourceOverride.isDeleted()) {
            return null;
        }
        return new SharedRecurrenceOccurrence(
                occurrence.originStartAt(),
                sourceOverride.getOverrideTitle(),
                sourceOverride.getOverrideDescription(),
                sourceOverride.getOverrideStartAt(),
                sourceOverride.getOverrideEndAt(),
                sourceOverride.isOverrideAllDay(),
                sourceOverride.getOverrideTimeZone()
        );
    }

    private GroupCalendarItemResponse sharedRecurrenceItem(
            PersonalRecurrenceGroupShare share,
            SharedRecurrenceOccurrence sourceOccurrence,
            Map<Long, String> nicknamesByAccountId
    ) {
        String nickname = nicknameOf(nicknamesByAccountId, share.getRecurrenceEvent().getAccount().getId());
        String anonymousTitle = nickname + "의 일정";
        String title = share.resolvePublicTitle(sourceOccurrence.title(), anonymousTitle);
        Instant startAt = sourceOccurrence.startAt();
        Instant endAt = sourceOccurrence.endAt();
        boolean allDay = sourceOccurrence.allDay();
        return GroupCalendarItemResponse.sharedRecurrenceOccurrence(
                title,
                share.isAnonymous() ? null : sourceOccurrence.description(),
                startAt,
                endAt,
                allDay,
                allDay ? null : sourceOccurrence.timeZone(),
                sourceOccurrence.originStartAt(),
                nickname,
                share.getId()
        );
    }

    private List<GroupCalendarItemResponse> listSharedOneOffEvents(
            Long groupSpaceId,
            Instant from,
            Instant to,
            Map<Long, String> nicknamesByAccountId
    ) {
        return personalEventShareQueryService.listSharesInGroupSpace(groupSpaceId)
                .stream()
                .map(share -> GroupCalendarItemResponse.sharedOneOff(
                        share.resolvePublicTitle(anonymousTitle(nicknamesByAccountId, share.getEvent().getAccount().getId())),
                        share.resolvePublicDescription(),
                        share.resolveStartAt(),
                        share.resolveEndAt(),
                        share.resolveAllDay(),
                        share.resolveAllDay() ? null : share.getEvent().getTimeZone(),
                        nicknameOf(nicknamesByAccountId, share.getEvent().getAccount().getId()),
                        share.getId()
                ))
                .filter(item -> overlaps(item, from, to))
                .toList();
    }

    private List<GroupCalendarItemResponse> listDirectEvents(
            Long groupSpaceId,
            Instant from,
            Instant to,
            Map<Long, String> nicknamesByAccountId
    ) {
        return eventQueryService.listOverlappingEvents(groupSpaceId, from, to)
                .stream()
                .map(event -> GroupCalendarItemResponse.from(
                        event,
                        nicknameOf(nicknamesByAccountId, event.getCreatedBy().getId())
                ))
                .toList();
    }

    private List<GroupCalendarItemResponse> listRecurrenceOccurrences(
            Long groupSpaceId,
            Instant from,
            Instant to,
            Map<Long, String> nicknamesByAccountId
    ) {
        List<GroupCalendarItemResponse> items = new ArrayList<>();
        Set<OccurrenceKey> occurrenceKeys = new HashSet<>();
        recurrenceQueryService.listExpansionCandidates(groupSpaceId, to).forEach(recurrenceEvent ->
                addExpandedOccurrences(
                        recurrenceEvent,
                        from,
                        to,
                        occurrenceKeys,
                        items,
                        nicknameOf(nicknamesByAccountId, recurrenceEvent.getCreatedBy().getId())
                )
        );
        addMovedInOverrides(groupSpaceId, from, to, occurrenceKeys, items, nicknamesByAccountId);
        return items;
    }

    private void addExpandedOccurrences(
            GroupCalendarRecurrenceEvent recurrenceEvent,
            Instant from,
            Instant to,
            Set<OccurrenceKey> occurrenceKeys,
            List<GroupCalendarItemResponse> items,
            String nickname
    ) {
        List<RecurrenceOccurrence> occurrences = recurrenceEngine.expand(
                recurrenceEvent.toRecurrenceSchedule(),
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
                    overridesByOrigin.get(occurrence.originStartAt()),
                    nickname
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
            GroupCalendarRecurrenceOverride override,
            String nickname
    ) {
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
            List<GroupCalendarItemResponse> items,
            Map<Long, String> nicknamesByAccountId
    ) {
        overrideQueryService.listMovedInOverrides(groupSpaceId, from, to)
                .stream()
                .filter(override -> occurrenceKeys.add(OccurrenceKey.from(override)))
                .map(override -> GroupCalendarItemResponse.recurrenceOverride(
                        override,
                        nicknameOf(
                                nicknamesByAccountId,
                                override.getRecurrenceEvent().getCreatedBy().getId()
                        )
                ))
                .forEach(items::add);
    }

    private Map<Long, String> listNicknames(Long groupSpaceId) {
        return membershipQueryService.listActiveMembers(groupSpaceId)
                .stream()
                .collect(Collectors.toMap(GroupMember::getAccountId, GroupMember::getNickname));
    }

    private String nicknameOf(Map<Long, String> nicknamesByAccountId, Long accountId) {
        return nicknamesByAccountId.get(accountId);
    }

    private String anonymousTitle(Map<Long, String> nicknamesByAccountId, Long accountId) {
        return nicknameOf(nicknamesByAccountId, accountId) + "의 일정";
    }

    private List<GroupCalendarItemResponse> mergeItems(List<GroupCalendarItemResponse> items) {
        return items.stream()
                .collect(Collectors.toMap(
                        this::itemKey,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .sorted(Comparator.comparing(GroupCalendarItemResponse::startAt))
                .toList();
    }

    private CalendarItemKey itemKey(GroupCalendarItemResponse item) {
        if (item.isSharedPersonalSchedule()) {
            return new CalendarItemKey(
                    "SHARED",
                    item.shareMappingId(),
                    item.isRecurrenceOccurrence() ? item.originStartAt() : null
            );
        }
        Long directItemId = item.isRecurrenceOccurrence() ? item.recurrenceId() : item.id();
        return new CalendarItemKey(
                item.isRecurrenceOccurrence() ? "DIRECT_RECURRENCE" : "DIRECT_EVENT",
                directItemId,
                item.originStartAt()
        );
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

    private record SharedOccurrenceKey(Long shareId, Instant originStartAt) {
    }

    private record CalendarItemKey(String originType, Long sourceId, Instant originStartAt) {
    }

    private record SharedRecurrenceOccurrence(
            Instant originStartAt,
            String title,
            String description,
            Instant startAt,
            Instant endAt,
            boolean allDay,
            String timeZone
    ) {
    }
}
