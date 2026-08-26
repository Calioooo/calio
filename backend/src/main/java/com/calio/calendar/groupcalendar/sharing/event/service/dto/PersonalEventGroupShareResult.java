package com.calio.calendar.groupcalendar.sharing.event.service.dto;

import java.util.List;

public record PersonalEventGroupShareResult(List<TargetResult> groupResults) {
    public record TargetResult(Long groupSpaceId, PersonalEventGroupShareTargetStatus status) {
    }
}
