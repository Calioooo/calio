package com.calio.calendar.groupcalendar.sharing.event.controller.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PersonalEventGroupShareRequest(
        @NotNull(message = "선택 공유 여부는 필수입니다.") Boolean selectionEnabled,
        List<@NotNull(message = "공유할 일정 ID는 필수입니다.") Long> eventIds,
        List<@NotNull(message = "공유할 Group Space ID는 필수입니다.") Long> groupSpaceIds
) {
    public List<Long> eventIdsOrEmpty() {
        return eventIds == null ? List.of() : eventIds;
    }

    public List<Long> groupSpaceIdsOrEmpty() {
        return groupSpaceIds == null ? List.of() : groupSpaceIds;
    }
}
