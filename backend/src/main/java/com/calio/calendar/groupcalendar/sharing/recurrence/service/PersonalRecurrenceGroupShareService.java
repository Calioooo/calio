package com.calio.calendar.groupcalendar.sharing.recurrence.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareScope;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareSelectedOrigin;
import com.calio.calendar.groupcalendar.sharing.recurrence.service.dto.PersonalRecurrenceGroupShareCommand;
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
    private final PersonalRecurrenceGroupShareCommandService shareCommandService;
    private final Rfc5545RecurrenceEngine recurrenceEngine;

    public PersonalRecurrenceGroupShareService(
            RecurrenceEventQueryService recurrenceEventQueryService,
            GroupMembershipQueryService membershipQueryService,
            PersonalRecurrenceGroupShareCommandService shareCommandService,
            Rfc5545RecurrenceEngine recurrenceEngine
    ) {
        this.recurrenceEventQueryService = recurrenceEventQueryService;
        this.membershipQueryService = membershipQueryService;
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
}
