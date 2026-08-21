package com.calio.calendar.groupcalendar.recurrence.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.groupcalendar.controller.dto.GroupCalendarItemResponse;
import com.calio.calendar.groupcalendar.recurrence.controller.dto.GroupCalendarRecurrenceOccurrenceRequest;
import com.calio.calendar.groupcalendar.recurrence.controller.dto.GroupCalendarRecurrenceRequest;
import com.calio.calendar.groupcalendar.recurrence.controller.dto.GroupCalendarRecurrenceResponse;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceEvent;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceOverride;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.groupspace.service.GroupSpaceCommandService;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.service.TagQueryService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GroupCalendarRecurrenceService {

    private final GroupSpaceCommandService groupSpaceCommandService;
    private final GroupMembershipQueryService membershipQueryService;
    private final AccountQueryService accountQueryService;
    private final TagQueryService tagQueryService;
    private final GroupCalendarRecurrenceQueryService recurrenceQueryService;
    private final GroupCalendarRecurrenceCommandService recurrenceCommandService;
    private final GroupCalendarRecurrenceOverrideQueryService overrideQueryService;
    private final GroupCalendarRecurrenceOverrideCommandService overrideCommandService;
    private final Rfc5545RecurrenceEngine recurrenceEngine;
    private final Clock clock;

    public GroupCalendarRecurrenceService(
            GroupSpaceCommandService groupSpaceCommandService,
            GroupMembershipQueryService membershipQueryService,
            AccountQueryService accountQueryService,
            TagQueryService tagQueryService,
            GroupCalendarRecurrenceQueryService recurrenceQueryService,
            GroupCalendarRecurrenceCommandService recurrenceCommandService,
            GroupCalendarRecurrenceOverrideQueryService overrideQueryService,
            GroupCalendarRecurrenceOverrideCommandService overrideCommandService,
            Rfc5545RecurrenceEngine recurrenceEngine,
            Clock clock
    ) {
        this.groupSpaceCommandService = groupSpaceCommandService;
        this.membershipQueryService = membershipQueryService;
        this.accountQueryService = accountQueryService;
        this.tagQueryService = tagQueryService;
        this.recurrenceQueryService = recurrenceQueryService;
        this.recurrenceCommandService = recurrenceCommandService;
        this.overrideQueryService = overrideQueryService;
        this.overrideCommandService = overrideCommandService;
        this.recurrenceEngine = recurrenceEngine;
        this.clock = clock;
    }

    @Transactional
    public GroupCalendarRecurrenceResponse create(
            Long accountId,
            Long groupSpaceId,
            GroupCalendarRecurrenceRequest request
    ) {
        GroupSpace groupSpace = groupSpaceCommandService.lockGroupSpace(groupSpaceId);
        membershipQueryService.getActiveMembership(groupSpaceId, accountId);

        RecurrenceSchedule schedule = createSchedule(request);
        List<String> recurrenceRules = recurrenceEngine.validate(schedule, request.recurrence());
        Account account = accountQueryService.getAccount(accountId);
        Tag tag = tagQueryService.getGroupTagOrDefault(groupSpaceId, request.tagId());
        GroupCalendarRecurrenceEvent event = new GroupCalendarRecurrenceEvent(
                groupSpace,
                account,
                tag,
                request.title(),
                request.description(),
                schedule,
                recurrenceRules
        );

        return GroupCalendarRecurrenceResponse.from(
                recurrenceCommandService.createRecurrenceEvent(event)
        );
    }

    public GroupCalendarRecurrenceResponse get(Long accountId, Long groupSpaceId, Long recurrenceId) {
        membershipQueryService.getActiveMembership(groupSpaceId, accountId);
        return GroupCalendarRecurrenceResponse.from(
                recurrenceQueryService.getRecurrenceEvent(groupSpaceId, recurrenceId)
        );
    }

    @Transactional
    public GroupCalendarRecurrenceResponse update(
            Long accountId,
            Long groupSpaceId,
            Long recurrenceId,
            GroupCalendarRecurrenceRequest request
    ) {
        GroupCalendarRecurrenceEvent event = recurrenceCommandService.lockRecurrenceEvent(
                groupSpaceId,
                recurrenceId
        );
        requireAuthorOrOwner(accountId, event);

        RecurrenceSchedule schedule = createSchedule(request);
        List<String> recurrenceRules = recurrenceEngine.validate(schedule, request.recurrence());
        Tag tag = tagQueryService.getGroupTagOrDefault(groupSpaceId, request.tagId());
        event.update(request.title(), request.description(), tag, schedule, recurrenceRules);

        return GroupCalendarRecurrenceResponse.from(event);
    }

    @Transactional
    public void delete(Long accountId, Long groupSpaceId, Long recurrenceId) {
        GroupCalendarRecurrenceEvent event = recurrenceCommandService.lockRecurrenceEvent(
                groupSpaceId,
                recurrenceId
        );
        requireAuthorOrOwner(accountId, event);
        recurrenceCommandService.deleteRecurrenceEvent(event);
    }

    @Transactional
    public GroupCalendarItemResponse updateOccurrence(
            Long accountId,
            Long groupSpaceId,
            Long recurrenceId,
            GroupCalendarRecurrenceOccurrenceRequest request
    ) {
        GroupCalendarRecurrenceEvent recurrenceEvent = recurrenceCommandService.lockRecurrenceEvent(
                groupSpaceId,
                recurrenceId
        );
        requireAuthorOrOwner(accountId, recurrenceEvent);

        CanonicalSchedule schedule = CanonicalSchedule.recurrenceOverride(
                request.startAt(),
                request.endAt(),
                request.allDay(),
                request.timeZone()
        );
        Optional<GroupCalendarRecurrenceOverride> existingOverride = getOverrideOrRejectOccurrence(
                recurrenceEvent,
                request.originStartAt()
        );
        GroupCalendarRecurrenceOverride override = existingOverride.orElseGet(() ->
                GroupCalendarRecurrenceOverride.active(
                        recurrenceEvent,
                        request.originStartAt(),
                        request.title(),
                        request.description(),
                        schedule
                )
        );
        if (existingOverride.isPresent()) {
            override.activate(request.title(), request.description(), schedule);
        }

        return GroupCalendarItemResponse.recurrenceOverride(
                overrideCommandService.createOrUpdateOverride(override),
                getCreatorNickname(recurrenceEvent)
        );
    }

    @Transactional
    public void deleteOccurrence(
            Long accountId,
            Long groupSpaceId,
            Long recurrenceId,
            Instant originStartAt
    ) {
        GroupCalendarRecurrenceEvent recurrenceEvent = recurrenceCommandService.lockRecurrenceEvent(
                groupSpaceId,
                recurrenceId
        );
        requireAuthorOrOwner(accountId, recurrenceEvent);

        Optional<GroupCalendarRecurrenceOverride> existingOverride = getOverrideOrRejectOccurrence(
                recurrenceEvent,
                originStartAt
        );
        GroupCalendarRecurrenceOverride override = existingOverride.orElseGet(() ->
                GroupCalendarRecurrenceOverride.deleted(recurrenceEvent, originStartAt, clock.instant())
        );
        if (existingOverride.isPresent()) {
            override.markDeleted(clock.instant());
        }
        overrideCommandService.createOrUpdateOverride(override);
    }

    private RecurrenceSchedule createSchedule(GroupCalendarRecurrenceRequest request) {
        return RecurrenceSchedule.create(
                request.allDay(),
                request.firstOccurrenceStartAt(),
                request.firstOccurrenceEndAt(),
                request.timeZone()
        );
    }

    private void requireAuthorOrOwner(Long accountId, GroupCalendarRecurrenceEvent event) {
        GroupMember member = membershipQueryService.getActiveMembership(event.getGroupSpace().getId(), accountId);
        boolean isAuthor = event.getCreatedBy().getId().equals(accountId);

        if (!isAuthor && !member.roleIn(event.getGroupSpace()).isOwner()) {
            throw new CalioException(ErrorCode.GROUP_RECURRENCE_EVENT_FORBIDDEN);
        }
    }

    private Optional<GroupCalendarRecurrenceOverride> getOverrideOrRejectOccurrence(
            GroupCalendarRecurrenceEvent recurrenceEvent,
            Instant originStartAt
    ) {
        Optional<GroupCalendarRecurrenceOverride> override = overrideQueryService.getOverrideIfExists(
                recurrenceEvent.getId(),
                originStartAt
        );
        if (override.isEmpty() && !containsOrigin(recurrenceEvent, originStartAt)) {
            throw new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
        }
        return override;
    }

    private boolean containsOrigin(GroupCalendarRecurrenceEvent recurrenceEvent, Instant originStartAt) {
        return recurrenceEngine.containsOrigin(
                new RecurrenceSchedule(
                        recurrenceEvent.getFirstOccurrenceStartAt(),
                        recurrenceEvent.getFirstOccurrenceEndAt(),
                        recurrenceEvent.isAllDay(),
                        recurrenceEvent.getTimeZone()
                ),
                recurrenceEvent.getRecurrenceRules(),
                originStartAt
        );
    }

    private String getCreatorNickname(GroupCalendarRecurrenceEvent recurrenceEvent) {
        return membershipQueryService
                .getActiveMembership(
                        recurrenceEvent.getGroupSpace().getId(),
                        recurrenceEvent.getCreatedBy().getId()
                )
                .getNickname();
    }
}
