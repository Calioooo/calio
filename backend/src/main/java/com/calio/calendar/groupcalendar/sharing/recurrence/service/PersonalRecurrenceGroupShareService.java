package com.calio.calendar.groupcalendar.sharing.recurrence.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareScope;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareSelectedOrigin;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareOccurrenceOverride;
import com.calio.calendar.groupcalendar.sharing.recurrence.service.dto.PersonalRecurrenceGroupShareCommand;
import com.calio.calendar.groupcalendar.sharing.recurrence.service.dto.UpdatePersonalRecurrenceGroupShareCommand;
import com.calio.calendar.groupcalendar.sharing.recurrence.service.dto.UpdatePersonalRecurrenceGroupShareOccurrenceOverrideCommand;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.service.RecurrenceEventQueryService;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PersonalRecurrenceGroupShareService {

    private final RecurrenceEventQueryService recurrenceEventQueryService;
    private final GroupMembershipQueryService membershipQueryService;
    private final PersonalRecurrenceGroupShareQueryService shareQueryService;
    private final PersonalRecurrenceGroupShareCommandService shareCommandService;
    private final Rfc5545RecurrenceEngine recurrenceEngine;

    public PersonalRecurrenceGroupShareService(
            RecurrenceEventQueryService recurrenceEventQueryService,
            GroupMembershipQueryService membershipQueryService,
            PersonalRecurrenceGroupShareQueryService shareQueryService,
            PersonalRecurrenceGroupShareCommandService shareCommandService,
            Rfc5545RecurrenceEngine recurrenceEngine
    ) {
        this.recurrenceEventQueryService = recurrenceEventQueryService;
        this.membershipQueryService = membershipQueryService;
        this.shareQueryService = shareQueryService;
        this.shareCommandService = shareCommandService;
        this.recurrenceEngine = recurrenceEngine;
    }

    @Transactional
    public void share(
            Long accountId,
            Long groupSpaceId,
            PersonalRecurrenceGroupShareCommand command
    ) {
        GroupMember membership = membershipQueryService.getActiveMembership(groupSpaceId, accountId);
        RecurrenceEvent recurrenceEvent = recurrenceEventQueryService.getRecurrenceEvent(
                accountId,
                command.recurrenceId()
        );
        PersonalRecurrenceGroupShareScope shareScope = resolveShareScope(command);
        PersonalRecurrenceGroupShare share = new PersonalRecurrenceGroupShare(
                recurrenceEvent,
                membership.getGroupSpace(),
                shareScope
        );
        PersonalRecurrenceGroupShare createdShare = shareCommandService.createShare(share);
        if (shareScope == PersonalRecurrenceGroupShareScope.SELECTED_OCCURRENCES) {
            selectOrigins(createdShare, command.originStartAts());
        }
    }

    private PersonalRecurrenceGroupShareScope resolveShareScope(
            PersonalRecurrenceGroupShareCommand command
    ) {
        if (!command.selectionEnabled()) {
            requireNoSelectedOrigins(command.originStartAts());
            return PersonalRecurrenceGroupShareScope.WHOLE_SERIES;
        }
        if (command.originStartAts().isEmpty()) {
            throw new CalioException(ErrorCode.VALIDATION_FAILED);
        }
        return PersonalRecurrenceGroupShareScope.SELECTED_OCCURRENCES;
    }

    private void requireNoSelectedOrigins(List<Instant> originStartAts) {
        if (!originStartAts.isEmpty()) {
            throw new CalioException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private void selectOrigins(PersonalRecurrenceGroupShare share, List<Instant> originStartAts) {
        Set<Instant> distinctOrigins = new LinkedHashSet<>(originStartAts);
        distinctOrigins.forEach(originStartAt -> selectOrigin(share, originStartAt));
    }

    private void selectOrigin(PersonalRecurrenceGroupShare share, Instant originStartAt) {
        if (!isValidOrigin(share.getRecurrenceEvent(), originStartAt)) {
            throw new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
        }
        shareCommandService.selectOrigin(new PersonalRecurrenceGroupShareSelectedOrigin(
                share,
                originStartAt
        ));
    }

    private boolean isValidOrigin(RecurrenceEvent recurrenceEvent, Instant originStartAt) {
        return recurrenceEngine.containsOrigin(
                RecurrenceSchedule.from(recurrenceEvent),
                recurrenceEvent.getRecurrenceRules(),
                originStartAt
        );
    }

    @Transactional
    public void update(
            Long accountId,
            Long groupSpaceId,
            Long recurrenceId,
            UpdatePersonalRecurrenceGroupShareCommand command
    ) {
        membershipQueryService.getActiveMembership(groupSpaceId, accountId);
        PersonalRecurrenceGroupShare share = getShare(recurrenceId, groupSpaceId);
        requireSourceOwner(accountId, share);
        shareCommandService.updateRepresentation(
                share,
                command.showOriginalDetails(),
                command.overrideTitle(),
                command.overrideStartAt(),
                command.overrideEndAt(),
                command.overrideAllDay()
        );
    }

    @Transactional
    public void updateOccurrence(
            Long accountId,
            Long groupSpaceId,
            Long recurrenceId,
            UpdatePersonalRecurrenceGroupShareOccurrenceOverrideCommand command
    ) {
        membershipQueryService.getActiveMembership(groupSpaceId, accountId);
        PersonalRecurrenceGroupShare share = getShare(recurrenceId, groupSpaceId);
        requireSourceOwner(accountId, share);
        requireValidOrigin(share.getRecurrenceEvent(), command.originStartAt());
        updateOccurrenceOverride(share, command);
    }

    @Transactional
    public void remove(Long accountId, Long groupSpaceId, Long recurrenceId) {
        GroupMember membership = membershipQueryService.getActiveMembership(groupSpaceId, accountId);
        PersonalRecurrenceGroupShare share = getShare(recurrenceId, groupSpaceId);
        requireSourceOwnerOrGroupOwner(accountId, membership, share);
        shareCommandService.deleteShare(share);
    }

    private PersonalRecurrenceGroupShare getShare(Long recurrenceId, Long groupSpaceId) {
        return shareQueryService.getShareIfExists(recurrenceId, groupSpaceId)
                .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
    }

    private void requireSourceOwner(Long accountId, PersonalRecurrenceGroupShare share) {
        if (!share.getRecurrenceEvent().getAccount().getId().equals(accountId)) {
            throw new CalioException(ErrorCode.PERSONAL_RECURRENCE_GROUP_SHARE_FORBIDDEN);
        }
    }

    private void requireSourceOwnerOrGroupOwner(
            Long accountId,
            GroupMember membership,
            PersonalRecurrenceGroupShare share
    ) {
        boolean isSourceOwner = share.getRecurrenceEvent().getAccount().getId().equals(accountId);
        if (!isSourceOwner && !membership.roleIn(share.getGroupSpace()).isOwner()) {
            throw new CalioException(ErrorCode.PERSONAL_RECURRENCE_GROUP_SHARE_FORBIDDEN);
        }
    }

    private void requireValidOrigin(RecurrenceEvent recurrenceEvent, Instant originStartAt) {
        if (!isValidOrigin(recurrenceEvent, originStartAt)) {
            throw new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
        }
    }

    private void updateOccurrenceOverride(
            PersonalRecurrenceGroupShare share,
            UpdatePersonalRecurrenceGroupShareOccurrenceOverrideCommand command
    ) {
        shareQueryService.getOccurrenceOverrideIfExists(share.getId(), command.originStartAt())
                .ifPresentOrElse(
                        occurrenceOverride -> shareCommandService.updateOccurrenceRepresentation(
                                occurrenceOverride,
                                command.overrideTitle(),
                                command.overrideStartAt(),
                                command.overrideEndAt(),
                                command.overrideAllDay()
                        ),
                        () -> createOccurrenceOverride(share, command)
                );
    }

    private void createOccurrenceOverride(
            PersonalRecurrenceGroupShare share,
            UpdatePersonalRecurrenceGroupShareOccurrenceOverrideCommand command
    ) {
        PersonalRecurrenceGroupShareOccurrenceOverride occurrenceOverride =
                new PersonalRecurrenceGroupShareOccurrenceOverride(share, command.originStartAt());
        occurrenceOverride.updateRepresentation(
                command.overrideTitle(),
                command.overrideStartAt(),
                command.overrideEndAt(),
                command.overrideAllDay()
        );
        shareCommandService.createOccurrenceOverride(occurrenceOverride);
    }
}
