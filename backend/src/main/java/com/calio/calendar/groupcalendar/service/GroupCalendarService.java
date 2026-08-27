package com.calio.calendar.groupcalendar.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupcalendar.controller.dto.GroupCalendarItemResponse;
import com.calio.calendar.groupcalendar.event.service.GroupCalendarEventQueryService;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceEvent;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceOverride;
import com.calio.calendar.groupcalendar.recurrence.service.GroupCalendarRecurrenceOverrideQueryService;
import com.calio.calendar.groupcalendar.recurrence.service.GroupCalendarRecurrenceQueryService;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.service.RecurrenceEventQueryService;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
import com.calio.calendar.sharing.event.service.PersonalEventGroupShareQueryService;
import com.calio.calendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import com.calio.calendar.sharing.recurrence.service.PersonalRecurrenceGroupShareQueryService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GroupCalendarService {

    private static final Duration MAX_QUERY_RANGE = Duration.ofDays(366);
    private static final int MAX_SHARED_RECURRENCE_OCCURRENCES = 5_000;

    private final GroupMembershipQueryService membershipQueryService;
    private final GroupCalendarEventQueryService eventQueryService;
    private final GroupCalendarRecurrenceQueryService recurrenceQueryService;
    private final GroupCalendarRecurrenceOverrideQueryService overrideQueryService;
    private final Rfc5545RecurrenceEngine recurrenceEngine;
    private final PersonalEventGroupShareQueryService eventShareQueryService;
    private final PersonalRecurrenceGroupShareQueryService recurrenceShareQueryService;
    private final RecurrenceEventQueryService personalRecurrenceQueryService;
    private final MeterRegistry meterRegistry;

    public GroupCalendarService(
            GroupMembershipQueryService membershipQueryService,
            GroupCalendarEventQueryService eventQueryService,
            GroupCalendarRecurrenceQueryService recurrenceQueryService,
            GroupCalendarRecurrenceOverrideQueryService overrideQueryService,
            Rfc5545RecurrenceEngine recurrenceEngine,
            PersonalEventGroupShareQueryService eventShareQueryService,
            PersonalRecurrenceGroupShareQueryService recurrenceShareQueryService,
            RecurrenceEventQueryService personalRecurrenceQueryService,
            MeterRegistry meterRegistry
    ) {
        this.membershipQueryService = membershipQueryService;
        this.eventQueryService = eventQueryService;
        this.recurrenceQueryService = recurrenceQueryService;
        this.overrideQueryService = overrideQueryService;
        this.recurrenceEngine = recurrenceEngine;
        this.eventShareQueryService = eventShareQueryService;
        this.recurrenceShareQueryService = recurrenceShareQueryService;
        this.personalRecurrenceQueryService = personalRecurrenceQueryService;
        this.meterRegistry = meterRegistry;
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
        items.addAll(listSharedEvents(groupSpaceId, from, to, nicknamesByAccountId));
        items.addAll(listRecurrenceOccurrences(groupSpaceId, from, to, nicknamesByAccountId));
        items.addAll(listSharedRecurrenceOccurrences(groupSpaceId, from, to, nicknamesByAccountId));
        items.sort(Comparator.comparing(GroupCalendarItemResponse::startAt));
        return items;
    }

    private List<GroupCalendarItemResponse> listSharedEvents(
            Long groupSpaceId,
            Instant from,
            Instant to,
            Map<Long, String> nicknamesByAccountId
    ) {
        return eventShareQueryService.listByGroupSpaceId(groupSpaceId).stream()
                .map(share -> GroupCalendarItemResponse.sharedEvent(
                        share,
                        nicknameOf(nicknamesByAccountId, share.getEvent().getAccount().getId())
                ))
                .filter(item -> overlaps(item, from, to))
                .toList();
    }

    private List<GroupCalendarItemResponse> listSharedRecurrenceOccurrences(
            Long groupSpaceId,
            Instant from,
            Instant to,
            Map<Long, String> nicknamesByAccountId
    ) {
        List<PersonalRecurrenceGroupShare> shares = recurrenceShareQueryService.listByGroupSpaceId(groupSpaceId);
        long overrideQueryStartedAt = System.nanoTime();
        List<RecurrenceEventOverride> overrides = personalRecurrenceQueryService
                .listOverridesForRecurrenceIdsInRange(
                        shares.stream().map(share -> share.getRecurrenceEvent().getId()).toList(), from, to
                );
        Timer.builder("group_calendar.shared_recurrence_override_query")
                .register(meterRegistry)
                .record(System.nanoTime() - overrideQueryStartedAt, TimeUnit.NANOSECONDS);
        Map<SourceOccurrenceKey, RecurrenceEventOverride> overridesBySourceOccurrence = overrides.stream()
                        .collect(Collectors.toMap(
                                override -> new SourceOccurrenceKey(
                                        override.getRecurrenceId(),
                                        override.getOriginStartAt()
                                ),
                                Function.identity()
                        ));
        List<GroupCalendarItemResponse> items = new ArrayList<>();
        Set<SharedOccurrenceKey> occurrenceKeys = new HashSet<>();
        int expandedOccurrenceCount = 0;
        for (PersonalRecurrenceGroupShare share : shares) {
            expandedOccurrenceCount += addSharedExpandedOccurrences(
                    share,
                    from,
                    to,
                    nicknamesByAccountId,
                    overridesBySourceOccurrence,
                    occurrenceKeys,
                    items
            );
        }
        addSharedMovedInOverrides(
                shares,
                from,
                to,
                nicknamesByAccountId,
                overridesBySourceOccurrence,
                occurrenceKeys,
                items
        );
        Counter.builder("group_calendar.shared_recurrence_mapping_count")
                .register(meterRegistry)
                .increment(shares.size());
        Counter.builder("group_calendar.shared_recurrence_expanded_occurrence_count")
                .register(meterRegistry)
                .increment(expandedOccurrenceCount);
        return items;
    }

    private int addSharedExpandedOccurrences(
            PersonalRecurrenceGroupShare share,
            Instant from,
            Instant to,
            Map<Long, String> nicknamesByAccountId,
            Map<SourceOccurrenceKey, RecurrenceEventOverride> overridesBySourceOccurrence,
            Set<SharedOccurrenceKey> occurrenceKeys,
            List<GroupCalendarItemResponse> items
    ) {
        var recurrenceEvent = share.getRecurrenceEvent();
        List<RecurrenceOccurrence> occurrences = recurrenceEngine.expand(
                RecurrenceSchedule.from(recurrenceEvent), recurrenceEvent.getRecurrenceRules(), from, to
        );
        for (RecurrenceOccurrence occurrence : occurrences) {
            RecurrenceEventOverride override = overridesBySourceOccurrence.get(
                    new SourceOccurrenceKey(recurrenceEvent.getId(), occurrence.originStartAt())
            );
            if (override != null && override.isDeleted()) {
                continue;
            }
            GroupCalendarItemResponse item = GroupCalendarItemResponse.sharedRecurrenceOccurrence(
                    share,
                    occurrence,
                    override,
                    nicknameOf(nicknamesByAccountId, recurrenceEvent.getAccount().getId())
            );
            if (overlaps(item, from, to)) {
                addSharedRecurrenceItem(item, occurrenceKeys, items);
            }
        }
        return occurrences.size();
    }

    private void addSharedMovedInOverrides(
            List<PersonalRecurrenceGroupShare> shares,
            Instant from,
            Instant to,
            Map<Long, String> nicknamesByAccountId,
            Map<SourceOccurrenceKey, RecurrenceEventOverride> overridesBySourceOccurrence,
            Set<SharedOccurrenceKey> occurrenceKeys,
            List<GroupCalendarItemResponse> items
    ) {
        Map<Long, PersonalRecurrenceGroupShare> sharesByRecurrenceId = shares.stream()
                .collect(Collectors.toMap(share -> share.getRecurrenceEvent().getId(), Function.identity()));
        for (RecurrenceEventOverride override : overridesBySourceOccurrence.values()) {
            if (override.isDeleted()) {
                continue;
            }
            PersonalRecurrenceGroupShare share = sharesByRecurrenceId.get(override.getRecurrenceId());
            if (share == null) {
                continue;
            }
            RecurrenceOccurrence occurrence = new RecurrenceOccurrence(
                    override.getOriginStartAt(), override.getOverrideStartAt(), override.getOverrideEndAt()
            );
            GroupCalendarItemResponse item = GroupCalendarItemResponse.sharedRecurrenceOccurrence(
                    share,
                    occurrence,
                    override,
                    nicknameOf(nicknamesByAccountId, share.getRecurrenceEvent().getAccount().getId())
            );
            if (overlaps(item, from, to)) {
                addSharedRecurrenceItem(item, occurrenceKeys, items);
            }
        }
    }

    private void addSharedRecurrenceItem(
            GroupCalendarItemResponse item,
            Set<SharedOccurrenceKey> occurrenceKeys,
            List<GroupCalendarItemResponse> items
    ) {
        SharedOccurrenceKey key = new SharedOccurrenceKey(item.publicItemId(), item.originStartAt());
        if (!occurrenceKeys.add(key)) {
            return;
        }
        if (items.size() >= MAX_SHARED_RECURRENCE_OCCURRENCES) {
            throw new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_LIMIT_EXCEEDED);
        }
        items.add(item);
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

    private record SourceOccurrenceKey(Long recurrenceId, Instant originStartAt) {
    }

    private record SharedOccurrenceKey(String publicItemId, Instant originStartAt) {
    }
}
