package com.calio.calendar.groupcalendar.sharing.recurrence.controller.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record UpdatePersonalRecurrenceGroupShareOccurrenceOverrideRequest(
        @NotNull(message = "반복 일정 원본 시작 시각은 필수입니다.") Instant originStartAt,
        String overrideTitle,
        Instant overrideStartAt,
        Instant overrideEndAt,
        Boolean overrideAllDay
) {
}
