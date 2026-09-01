package com.calio.calendar.sharing.controller.dto;

import com.calio.calendar.sharing.event.domain.PersonalEventGroupShare;
import com.calio.calendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import java.util.UUID;

public record GroupShareStatusResponse(
        Long groupSpaceId,
        String groupSpaceName,
        boolean isAnonymous,
        UUID publicShareId
) {

    public static GroupShareStatusResponse from(PersonalEventGroupShare share) {
        return new GroupShareStatusResponse(
                share.getGroupSpace().getId(),
                share.getGroupSpace().getName(),
                share.isAnonymous(),
                share.getPublicShareId()
        );
    }

    public static GroupShareStatusResponse from(PersonalRecurrenceGroupShare share) {
        return new GroupShareStatusResponse(
                share.getGroupSpace().getId(),
                share.getGroupSpace().getName(),
                share.isAnonymous(),
                share.getPublicShareId()
        );
    }
}
