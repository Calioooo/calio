package com.calio.calendar.groupcalendar.sharing.recurrence.controller.dto;

import jakarta.validation.constraints.NotNull;

public record PersonalRecurrenceGroupShareRequest(
        @NotNull(message = "공유할 반복 일정 ID는 필수입니다.") Long recurrenceId
) {
}
