package com.calio.calendar.sharing.recurrence.controller.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateRecurrenceGroupSharesRequest(
        @NotEmpty(message = "공유할 그룹은 하나 이상 선택해야 합니다.")
        List<@NotNull(message = "공유할 그룹 선택에는 빈 값이 포함될 수 없습니다.") Long> groupSpaceIds
) {
}
