package com.calio.calendar.groupcalendar.sharing.event.controller.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SelectedPersonalEventGroupShareRequest(
        @NotEmpty(message = "공유할 일정은 하나 이상 선택해야 합니다.")
        List<@NotNull(message = "공유할 일정 ID는 필수입니다.") Long> eventIds
) {
}
