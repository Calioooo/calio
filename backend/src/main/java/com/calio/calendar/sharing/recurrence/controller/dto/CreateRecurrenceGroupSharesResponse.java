package com.calio.calendar.sharing.recurrence.controller.dto;

import com.calio.calendar.sharing.controller.dto.GroupShareTargetResponse;
import java.util.List;

public record CreateRecurrenceGroupSharesResponse(Long recurrenceId, List<GroupShareTargetResponse> targets) {

    public static CreateRecurrenceGroupSharesResponse from(Long recurrenceId, List<GroupShareTargetResponse> targets) {
        return new CreateRecurrenceGroupSharesResponse(recurrenceId, targets);
    }
}
