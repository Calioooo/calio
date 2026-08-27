package com.calio.calendar.sharing.event.controller.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateEventGroupSharesRequest(
        @NotEmpty(message = "공유할 일정은 하나 이상 선택해야 합니다.")
        List<@NotNull(message = "공유할 일정 선택에는 빈 값이 포함될 수 없습니다.") Long> eventIds,
        @NotEmpty(message = "공유할 그룹은 하나 이상 선택해야 합니다.")
        List<@NotNull(message = "공유할 그룹 선택에는 빈 값이 포함될 수 없습니다.") Long> groupSpaceIds,
        @NotNull(message = "익명 공유 여부는 필수입니다.") Boolean isAnonymous
) {
}
