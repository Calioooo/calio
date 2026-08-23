package com.calio.calendar.groupcalendar.sharing.recurrence.controller.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public record PersonalRecurrenceGroupShareRequest(
        @NotNull(message = "공유할 반복 일정 ID는 필수입니다.") Long recurrenceId,
        @NotNull(message = "선택 회차 공유 여부는 필수입니다.") Boolean selectionEnabled,
        List<@NotNull(message = "공유할 반복 회차 원본 시작 시각은 필수입니다.") Instant> originStartAts
) {
    public List<Instant> originStartAtsOrEmpty() {
        return originStartAts == null ? List.of() : originStartAts;
    }
}
