package com.calio.calendar.recurrence.controller.dto;

import com.calio.calendar.recurrence.domain.RecurrenceFrequency;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateRecurrenceEventRequest(
        @NotBlank(message = "반복 일정 제목은 공백일 수 없습니다.") String title,
        String description,
        @NotNull(message = "반복 일정 시작 날짜는 필수입니다.") LocalDate startDate,
        @NotNull(message = "반복 일정 종료 날짜는 필수입니다.") LocalDate endDate,
        @NotNull(message = "반복 일정 시작 시각은 필수입니다.") LocalTime startTime,
        @NotNull(message = "반복 일정 종료 시각은 필수입니다.") LocalTime endTime,
        @NotNull(message = "반복 일정 주기는 필수입니다.") RecurrenceFrequency recurrenceFrequency,
        Long tagId
) {
}
