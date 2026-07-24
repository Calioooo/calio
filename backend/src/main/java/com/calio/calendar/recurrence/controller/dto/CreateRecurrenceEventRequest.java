package com.calio.calendar.recurrence.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateRecurrenceEventRequest(
        @NotBlank(message = "반복 일정 제목은 공백일 수 없습니다.") String title,
        String description,
        @NotNull(message = "종일 여부는 필수입니다.") Boolean allDay,
        @NotNull(message = "반복 일정 시작 날짜는 필수입니다.") LocalDate startDate,
        @NotNull(message = "반복 일정 종료 날짜는 필수입니다.") LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        String timeZone,
        @NotNull(message = "반복 규칙은 필수입니다.") List<String> recurrence,
        Long tagId
) {
}
