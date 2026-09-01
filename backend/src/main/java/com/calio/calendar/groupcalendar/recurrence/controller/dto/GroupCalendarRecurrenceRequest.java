package com.calio.calendar.groupcalendar.recurrence.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public record GroupCalendarRecurrenceRequest(
        @NotBlank(message = "반복 일정 제목은 공백일 수 없습니다.") String title,
        String description,
        @NotNull(message = "종일 여부는 필수입니다.") Boolean allDay,
        @NotNull(message = "첫 일정 시작 시각은 필수입니다.") Instant firstOccurrenceStartAt,
        @NotNull(message = "첫 일정 종료 시각은 필수입니다.") Instant firstOccurrenceEndAt,
        String timeZone,
        @NotNull(message = "반복 규칙은 필수입니다.") List<String> recurrence,
        Long tagId
) {
}
