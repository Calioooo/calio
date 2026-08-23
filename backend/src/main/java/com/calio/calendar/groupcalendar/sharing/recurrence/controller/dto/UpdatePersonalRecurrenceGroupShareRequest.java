package com.calio.calendar.groupcalendar.sharing.recurrence.controller.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record UpdatePersonalRecurrenceGroupShareRequest(
        @NotNull(message = "원본 일정 상세 표시 여부는 필수입니다.") Boolean showOriginalDetails,
        String overrideTitle,
        Instant overrideStartAt,
        Instant overrideEndAt,
        Boolean overrideAllDay
) {
}
