package com.calio.calendar.groupcalendar.sharing.event.service.dto;

public record PersonalEventGroupShareStatusResponse(
        Long groupSpaceId,
        String groupSpaceName,
        boolean anonymous
) {
}
