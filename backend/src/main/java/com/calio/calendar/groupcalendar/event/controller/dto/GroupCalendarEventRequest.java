package com.calio.calendar.groupcalendar.event.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record GroupCalendarEventRequest(
        @NotBlank(message = "이벤트 제목은 공백일 수 없습니다.") String title,
        String description,
        @NotNull(message = "이벤트 시작 시각은 필수입니다.") Instant startAt,
        @NotNull(message = "이벤트 종료 시각은 필수입니다.") Instant endAt,
        @NotNull(message = "종일 일정 여부는 필수입니다.") Boolean allDay,
        String timeZone,
        Long tagId
) {
}
