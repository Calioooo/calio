package com.calio.calendar.sharing.event.controller.dto;

import com.calio.calendar.sharing.controller.dto.GroupShareTargetResponse;
import java.util.List;

public record EventGroupShareResultResponse(Long eventId, List<GroupShareTargetResponse> targets) {

    public static EventGroupShareResultResponse from(Long eventId, List<GroupShareTargetResponse> targets) {
        return new EventGroupShareResultResponse(eventId, targets);
    }
}
