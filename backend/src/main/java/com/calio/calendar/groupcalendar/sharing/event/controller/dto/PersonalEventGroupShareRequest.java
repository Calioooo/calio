package com.calio.calendar.groupcalendar.sharing.event.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PersonalEventGroupShareRequest(
        @NotNull(message = "선택 공유 여부는 필수입니다.") Boolean selectionEnabled,
        @Size(max = 100, message = "공유할 일정은 최대 100개까지 선택할 수 있습니다.")
        List<@NotNull(message = "공유할 일정 정보를 확인해 주세요.") Long> eventIds,
        @Size(max = 20, message = "공유할 그룹은 최대 20개까지 선택할 수 있습니다.")
        List<@NotNull(message = "공유할 그룹 정보를 확인해 주세요.") Long> groupSpaceIds
) {
    public List<Long> eventIdsOrEmpty() {
        return eventIds == null ? List.of() : eventIds;
    }

    public List<Long> groupSpaceIdsOrEmpty() {
        return groupSpaceIds == null ? List.of() : groupSpaceIds;
    }
}
