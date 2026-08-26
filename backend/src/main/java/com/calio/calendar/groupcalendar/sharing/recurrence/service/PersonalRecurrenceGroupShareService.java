package com.calio.calendar.groupcalendar.sharing.recurrence.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import com.calio.calendar.groupcalendar.sharing.recurrence.service.dto.PersonalRecurrenceGroupShareCommand;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.service.RecurrenceEventQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PersonalRecurrenceGroupShareService {

    private final RecurrenceEventQueryService recurrenceEventQueryService;
    private final GroupMembershipQueryService membershipQueryService;
    private final PersonalRecurrenceGroupShareQueryService shareQueryService;
    private final PersonalRecurrenceGroupShareCommandService shareCommandService;

    public PersonalRecurrenceGroupShareService(
            RecurrenceEventQueryService recurrenceEventQueryService,
            GroupMembershipQueryService membershipQueryService,
            PersonalRecurrenceGroupShareQueryService shareQueryService,
            PersonalRecurrenceGroupShareCommandService shareCommandService
    ) {
        this.recurrenceEventQueryService = recurrenceEventQueryService;
        this.membershipQueryService = membershipQueryService;
        this.shareQueryService = shareQueryService;
        this.shareCommandService = shareCommandService;
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
        PersonalRecurrenceGroupShare share = new PersonalRecurrenceGroupShare(
                recurrenceEvent,
                membership.getGroupSpace()
        );
        shareCommandService.createShare(share);
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

}
