package com.calio.calendar.groupcalendar.sharing.event.service.dto;

import java.time.Instant;

public record UpdatePersonalEventGroupShareCommand(
        boolean showOriginalDetails,
        String overrideTitle,
        Instant overrideStartAt,
        Instant overrideEndAt,
        Boolean overrideAllDay
) {
}
